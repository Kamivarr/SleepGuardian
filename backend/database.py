import os
from sqlalchemy.ext.asyncio import create_async_engine, async_sessionmaker
from sqlalchemy.orm import declarative_base

# TODO: Move database credentials to environment variables (.env) before production deployment
DATABASE_URL = os.getenv(
    "DATABASE_URL", 
    "postgresql+asyncpg://admin:tajnehaslo@localhost:5432/sleepguardian"
)

engine = create_async_engine(DATABASE_URL, echo=False)
SessionLocal = async_sessionmaker(bind=engine, expire_on_commit=False)
Base = declarative_base()

async def get_db():
    """Dependency for providing a database session per request."""
    async with SessionLocal() as session:
        yield session