import pytest
from httpx import AsyncClient, ASGITransport
from sqlalchemy.ext.asyncio import create_async_engine, async_sessionmaker

from main import app
from database import Base, get_db

# Configure an isolated in-memory SQLite database for integration testing.

TEST_DATABASE_URL = "sqlite+aiosqlite:///:memory:"
engine_test = create_async_engine(TEST_DATABASE_URL, echo=False)
TestingSessionLocal = async_sessionmaker(
    bind=engine_test,
    expire_on_commit=False,
)


async def override_get_db():
    """Provide a test database session for dependency injection."""
    async with TestingSessionLocal() as session:
        yield session


# Override the production database dependency with the test database.

app.dependency_overrides[get_db] = override_get_db


# Create database schema before each test and remove it afterward.

@pytest.fixture(autouse=True)
async def setup_database():
    async with engine_test.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)

    yield

    async with engine_test.begin() as conn:
        await conn.run_sync(Base.metadata.drop_all)


# Provide an asynchronous HTTP client for API endpoint testing.

@pytest.fixture
async def client():
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://testserver") as ac:
        yield ac


# ------------------------------------------------------------------
# Integration Test Scenarios
# ------------------------------------------------------------------

TEST_USER = {
    "email": "student@prz.edu.pl",
    "password": "tajnehaslo123",
}


@pytest.mark.asyncio
async def test_scenario_1_register(client: AsyncClient):
    """Verify successful registration of a new user account."""
    response = await client.post("/api/auth/register", json=TEST_USER)

    assert response.status_code == 201
    assert response.json()["message"] == "Account created successfully."


@pytest.mark.asyncio
async def test_scenario_2_login(client: AsyncClient):
    """Verify user authentication and JWT token generation."""

    # Register the user before attempting authentication.
    await client.post("/api/auth/register", json=TEST_USER)

    response = await client.post("/api/auth/login", json=TEST_USER)

    assert response.status_code == 200

    data = response.json()
    assert "token" in data
    assert data["message"] == "Login successful."


@pytest.mark.asyncio
async def test_scenario_3_start_session(client: AsyncClient):
    """Verify that an authenticated user can start a sleep session."""

    await client.post("/api/auth/register", json=TEST_USER)
    login_res = await client.post("/api/auth/login", json=TEST_USER)
    token = login_res.json()["token"]

    headers = {"Authorization": f"Bearer {token}"}
    payload = {
        "target_sleep_time": "22:00",
        "target_wake_time": "06:00",
    }

    response = await client.post(
        "/api/sleep/start",
        json=payload,
        headers=headers,
    )

    assert response.status_code == 201
    assert "session_id" in response.json()


@pytest.mark.asyncio
async def test_scenario_4_log_penalty(client: AsyncClient):
    """Verify penalty registration and heart deduction logic."""

    await client.post("/api/auth/register", json=TEST_USER)
    token = (
        await client.post("/api/auth/login", json=TEST_USER)
    ).json()["token"]

    headers = {"Authorization": f"Bearer {token}"}

    # Start a new sleep session.
    session_id = (
        await client.post(
            "/api/sleep/start",
            json={
                "target_sleep_time": "22:00",
                "target_wake_time": "06:00",
            },
            headers=headers,
        )
    ).json()["session_id"]

    # Submit a penalty event for the active session.
    penalty_payload = {"penalty_type": "Test Penalty"}

    response = await client.post(
        f"/api/sleep/penalty/{session_id}",
        json=penalty_payload,
        headers=headers,
    )

    assert response.status_code == 201

    data = response.json()

    # Assuming the user starts with 3 hearts,
    # a single penalty should reduce the count to 2.
    assert data["hearts_remaining"] == 2
    assert data["streak_lost"] is False


@pytest.mark.asyncio
async def test_scenario_5_end_session_and_check_history(
    client: AsyncClient,
):
    """Verify session completion and persistence in session history."""

    await client.post("/api/auth/register", json=TEST_USER)
    token = (
        await client.post("/api/auth/login", json=TEST_USER)
    ).json()["token"]

    headers = {"Authorization": f"Bearer {token}"}

    session_id = (
        await client.post(
            "/api/sleep/start",
            json={
                "target_sleep_time": "22:00",
                "target_wake_time": "06:00",
            },
            headers=headers,
        )
    ).json()["session_id"]

    # Complete the active sleep session.
    end_res = await client.post(
        f"/api/sleep/end/{session_id}",
        headers=headers,
    )

    assert end_res.status_code == 200

    # Verify that the completed session is available in history.
    hist_res = await client.get(
        "/api/sleep/history",
        headers=headers,
    )

    assert hist_res.status_code == 200
    assert len(hist_res.json()["history"]) == 1
    assert hist_res.json()["history"][0]["id"] == session_id