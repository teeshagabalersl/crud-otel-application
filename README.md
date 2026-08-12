# Observability Skeleton 

## Setup

1. Clone repository and cd into project directory.
2. Verify Java and Maven:
   java -version
   mvn -v
3. Build:
   mvn clean package
4. Run:
   mvn spring-boot:run

Application runs on http://localhost:8080

## Exposed Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST   | /users   | Create a user. Request JSON: {"name":"...","email":"..."}. Returns 201 and created user. |
| GET    | /users/{id} | Retrieve a user by id. Returns 200 and user, or 404 if not found. |
| DELETE | /users/{id} | Delete a user by id. Returns 204 on success, or 404 if not found. |

Example curl commands:

Create user:
curl -i -X POST http://localhost:8080/users \
  -H "Content-Type: application/json" \
  -d '{"name":"Alice","email":"alice@example.com"}'

Get user:
curl -i http://localhost:8080/users/1

Delete user:
curl -i -X DELETE http://localhost:8080/users/1
