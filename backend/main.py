from contextlib import asynccontextmanager
from datetime import datetime

from fastapi import FastAPI, HTTPException, Depends
from fastapi.security import OAuth2PasswordBearer
from pydantic import BaseModel
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.future import select
from sqlalchemy import func
from jose import JWTError, jwt

from database import engine, Base, get_db
import models
import security

@asynccontextmanager
async def lifespan(app: FastAPI):
    """Initializes database connections and executes required migrations on startup."""
    async with engine.begin() as conn:
        await conn.run_sync(models.Base.metadata.create_all)
    yield

app = FastAPI(title="SleepGuardian API", version="1.0.0", lifespan=lifespan)

oauth2_scheme = OAuth2PasswordBearer(tokenUrl="api/auth/login")


async def get_current_user_email(token: str = Depends(oauth2_scheme)) -> str:
    """Validates the JWT token and extracts the user subject (email)."""
    try:
        payload = jwt.decode(token, security.SECRET_KEY, algorithms=[security.ALGORITHM])
        email: str = payload.get("sub")
        if email is None:
            raise HTTPException(status_code=401, detail="Invalid authentication credentials.")
        return email
    except JWTError:
        raise HTTPException(status_code=401, detail="Invalid or expired token.")


class RegisterRequest(BaseModel):
    email: str
    password: str

class LoginRequest(BaseModel):
    email: str
    password: str

class LoginResponse(BaseModel):
    token: str
    message: str

class StartSessionRequest(BaseModel):
    target_sleep_time: str
    target_wake_time: str

class LogPenaltyRequest(BaseModel):
    penalty_type: str


@app.post("/api/auth/register", status_code=201)
async def register(request: RegisterRequest, db: AsyncSession = Depends(get_db)):
    """Registers a new user account."""
    result = await db.execute(select(models.User).where(models.User.email == request.email))
    if result.scalars().first():
        raise HTTPException(status_code=400, detail="Email is already registered.")
    
    hashed_pwd = security.get_password_hash(request.password)
    new_user = models.User(email=request.email, password_hash=hashed_pwd)
    
    db.add(new_user)
    await db.commit()
    return {"message": "Account created successfully."}


@app.post("/api/auth/login", response_model=LoginResponse)
async def login(request: LoginRequest, db: AsyncSession = Depends(get_db)):
    """Authenticates a user and returns a JWT token."""
    result = await db.execute(select(models.User).where(models.User.email == request.email))
    user = result.scalars().first()
    
    if not user or not security.verify_password(request.password, user.password_hash):
        raise HTTPException(status_code=401, detail="Invalid credentials.")
    
    access_token = security.create_access_token(data={"sub": user.email})
    return {"token": access_token, "message": "Login successful."}


@app.post("/api/sleep/start", status_code=201)
async def start_sleep_session(request: StartSessionRequest, current_user: str = Depends(get_current_user_email), db: AsyncSession = Depends(get_db)):
    """Starts a new sleep session for the authenticated user."""
    result = await db.execute(select(models.User).where(models.User.email == current_user))
    user = result.scalars().first()
    
    new_session = models.SleepSession(
        user_id=user.id,
        target_sleep_time=request.target_sleep_time,
        target_wake_time=request.target_wake_time
    )
    db.add(new_session)
    await db.commit()
    await db.refresh(new_session)
    return {"session_id": new_session.id, "message": "Session started successfully."}


@app.post("/api/sleep/end/{session_id}")
async def end_sleep_session(session_id: int, current_user: str = Depends(get_current_user_email), db: AsyncSession = Depends(get_db)):
    """Terminates an active sleep session."""
    result = await db.execute(select(models.SleepSession).where(models.SleepSession.id == session_id))
    session = result.scalars().first()
    
    if not session:
        raise HTTPException(status_code=404, detail="Session not found.")
        
    session.end_time = datetime.utcnow()
    await db.commit()
    return {"message": "Session ended successfully."}


@app.post("/api/sleep/penalty/{session_id}", status_code=201)
async def log_penalty_event(session_id: int, request: LogPenaltyRequest, current_user: str = Depends(get_current_user_email), db: AsyncSession = Depends(get_db)):
    """Logs a discipline violation, decrements health, and punishes the user if hearts run out."""
    result_user = await db.execute(select(models.User).where(models.User.email == current_user))
    user = result_user.scalars().first()
    
    new_log = models.PenaltyLog(session_id=session_id, penalty_type=request.penalty_type)
    db.add(new_log)

    penalty_message = "Kara zarejestrowana. Uważaj na swoje serca!"
    
    if user.hearts > 0:
        user.hearts -= 1
        
    if user.hearts == 0 and user.current_streak > 0:
        user.current_streak = 0
        penalty_message = "Straciłeś wszystkie serca! Twój Streak zrównał się z ziemią. Zawiodłeś."
    
    await db.commit()
    
    return {
        "message": penalty_message,
        "hearts_remaining": user.hearts,
        "streak_lost": user.hearts == 0
    }


@app.get("/api/sleep/stats")
async def get_sleep_statistics(current_user: str = Depends(get_current_user_email), db: AsyncSession = Depends(get_db)):
    """Aggregates sleep and penalty telemetry for the user profile."""
    result = await db.execute(select(models.User).where(models.User.email == current_user))
    user = result.scalars().first()

    sessions_result = await db.execute(
        select(func.count(models.SleepSession.id)).where(models.SleepSession.user_id == user.id)
    )
    total_sessions = sessions_result.scalar() or 0

    penalties_result = await db.execute(
        select(func.count(models.PenaltyLog.id))
        .join(models.SleepSession)
        .where(models.SleepSession.user_id == user.id)
    )
    total_penalties = penalties_result.scalar() or 0

    breakdown_result = await db.execute(
        select(models.PenaltyLog.penalty_type, func.count(models.PenaltyLog.id))
        .join(models.SleepSession)
        .where(models.SleepSession.user_id == user.id)
        .group_by(models.PenaltyLog.penalty_type)
    )
    breakdown = {row[0]: row[1] for row in breakdown_result.all()}

    return {
        "total_sessions": total_sessions,
        "total_penalties": total_penalties,
        "penalty_breakdown": breakdown,
        "current_streak": user.current_streak,
        "hearts": user.hearts
    }

@app.get("/api/sleep/history")
async def get_sleep_history(current_user: str = Depends(get_current_user_email), db: AsyncSession = Depends(get_db)):
    """Returns the sleep session history for the user."""
    result_user = await db.execute(select(models.User).where(models.User.email == current_user))
    user = result_user.scalars().first()

    sessions_result = await db.execute(
        select(models.SleepSession)
        .where(models.SleepSession.user_id == user.id)
        .order_by(models.SleepSession.id.desc())
        .limit(20)
    )
    sessions = sessions_result.scalars().all()

    history = []
    for s in sessions:
        history.append({
            "id": s.id,
            "start_time": s.start_time.isoformat() if s.start_time else None,
            "end_time": s.end_time.isoformat() if s.end_time else None,
            "target_sleep_time": s.target_sleep_time,
            "target_wake_time": s.target_wake_time
        })
    return {"history": history}