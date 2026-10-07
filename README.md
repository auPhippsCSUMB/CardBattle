## IMPORTANT RUNNING STEPS

### As of Oct 6:
- A private SSH tunnel is being used to connect to Railway Postgres
  - This may be changed to a public port if necessary
- There is a needed .env file to run the program
- You will need to run gradle build, then run ./gradlew bootrun

### A note:
- You can always contact me (Austin) if you have trouble with this, or use codex...
 
1. Get .env from group chat
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

8. run
```bash
source .env
set +a

bash ./gradlew bootRun
```
9. Leave this terminal open, it is your spring boot app
10. Your android studio project should be linked to the backend now! Database and oauth should be functional

This may very well be deprecated and replaced tomorrow if the public railway works instead, but for now, this is how we run our app