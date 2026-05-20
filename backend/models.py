from sqlalchemy import Column, Integer, String, DateTime, ForeignKey
from sqlalchemy.orm import relationship
from datetime import datetime
from database import Base

class User(Base):
    __tablename__ = "users"

    id = Column(Integer, primary_key=True, index=True)
    email = Column(String, unique=True, index=True, nullable=False)
    password_hash = Column(String, nullable=False)
    created_at = Column(DateTime, default=datetime.utcnow)

    current_streak = Column(Integer, default=0, nullable=False) 
    longest_streak = Column(Integer, default=0, nullable=False)  
    hearts = Column(Integer, default=3, nullable=False)          

    sessions = relationship("SleepSession", back_populates="user", cascade="all, delete-orphan")


class SleepSession(Base):
    __tablename__ = "sleep_sessions"

    id = Column(Integer, primary_key=True, index=True)
    user_id = Column(Integer, ForeignKey("users.id", ondelete="CASCADE"), nullable=False)
    start_time = Column(DateTime, default=datetime.utcnow, nullable=False)
    end_time = Column(DateTime, nullable=True)  # Null oznacza, że sesja nadal trwa
    target_sleep_time = Column(String, nullable=False)  # np. "22:00"
    target_wake_time = Column(String, nullable=False)   # np. "06:00"

    user = relationship("User", back_populates="sessions")
    penalties = relationship("PenaltyLog", back_populates="session", cascade="all, delete-orphan")


class PenaltyLog(Base):
    __tablename__ = "penalty_logs"

    id = Column(Integer, primary_key=True, index=True)
    session_id = Column(Integer, ForeignKey("sleep_sessions.id", ondelete="CASCADE"), nullable=False)
    penalty_type = Column(String, nullable=False)  # np. "Test Kamienia", "Upierdliwy Komar"
    triggered_at = Column(DateTime, default=datetime.utcnow, nullable=False)

    session = relationship("SleepSession", back_populates="penalties")