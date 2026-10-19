![CI](https://github.com/auPhippsCSUMB/CardBattle/actions/workflows/ci.yml/badge.svg)

## IMPORTANT RUNNING STEPS

### Docker quick start
- Copy `.env.example` to `.env` and fill in the values you want for your local environment.
- The compose file reads runtime values from your shell or `.env` file instead of hardcoding them into the project.
- Start the local database:
  ```bash
  docker compose up --build
  ```
- Start the API as an optional profile when needed:
  ```bash
  docker compose --profile api up --build
  ```
- PostgreSQL is available at `localhost:5432` and the API is available at `http://localhost:8080` when the API profile is enabled.
- To stop everything:
  ```bash
  docker compose down
  ```

### As of Oct 6:
- A private SSH tunnel is being used to connect to Railway Postgres
  - This may be changed to a public port if necessary
- There is a needed .env file to run the program
- You will need to run gradle build, then run ./gradlew bootrun

### A note:
- You can always contact me (Austin) if you have trouble with this, or use codex...
 
1. Get .env from group chat
~~
2. run
```bash
npm install -g @railway/cli 
railway login
```
3. run
```bash
railway link
```
then select the project we are working on

4. run 
```bash
ssh-keygen \
  -t ed25519 \
  -C "cardbattle-railway" \
  -f ~/.ssh/id_ed25519_railway
```
5. run
```bash
railway ssh keys add \ 
  --key ~/.ssh/id_ed25519_railway.pub \
  --name "cardbattle-laptop"
```
6. run
```bash
railway connect postgres --tunnel-only  
```
7. you will receive something like: 
```bash
Host:     127.0.0.1
Port:     EXAMPLE_PORT
User:     postgres
Password: EXAMPLE_PASSWORD
Database: railway
```
replace the number after the **localhost:** with the port number
Leave this terminal open, it is your postgres server, open a new terminal
~~

8. run
```bash
source .env
set +a

bash ./gradlew bootRun
```
9. Leave this terminal open, it is your spring boot app
10. Your android studio project should be linked to the backend now! Database and oauth should be functional

### NOTE
I am too lazy to remove steps 2-7, but they aren't necessary with the new .env with the public railway.
you may have to put <YOUR_ROUTE_TO_THE_PROJECT>/Android/Sdk/platform-tools/adb shell pm grant com.example.cardbattle android.permission.ACCESS_LOCAL_NETWORK
into the terminal to get login to work as Android 17 has some weird local network blocking

This may very well be deprecated and replaced tomorrow if the public railway works instead, but for now, this is how we run our app
^^^ TRUE!!!
