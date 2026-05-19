from contextlib import asynccontextmanager
from fastapi import FastAPI, HTTPException, Depends
from pydantic import BaseModel
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.future import select

from database import engine, Base, get_db
import models
import security

@asynccontextmanager
async def lifespan(app: FastAPI):
    """Handles application startup and shutdown events (e.g., DB migrations)."""
    async with engine.begin() as conn:
        await conn.run_sync(models.Base.metadata.create_all)
    yield

app = FastAPI(title="SleepGuardian API", version="1.0.0", lifespan=lifespan)

class RegisterRequest(BaseModel):
    email: str
    password: str

class LoginRequest(BaseModel):
    email: str
    password: str

class LoginResponse(BaseModel):
    token: str
    message: str

@app.post("/api/auth/register", status_code=201)
async def register(request: RegisterRequest, db: AsyncSession = Depends(get_db)):
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
    result = await db.execute(select(models.User).where(models.User.email == request.email))
    user = result.scalars().first()
    
    if not user or not security.verify_password(request.password, user.password_hash):
        raise HTTPException(status_code=401, detail="Invalid credentials.")
    
    access_token = security.create_access_token(data={"sub": user.email})
    return {"token": access_token, "message": "Login successful."}