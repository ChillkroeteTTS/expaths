import subprocess
import datetime
import ast


unix_strs = subprocess.check_output(["git", "log", '--pretty=format:"%ct"'])
last_unix = unix_strs.split("\n")[0][1:-1]
print(last_unix)

last_time = datetime.datetime.fromtimestamp(int(float(last_unix)))
print(last_time)
now = datetime.datetime.now()
delta = now-last_time

if (delta.total_seconds() < 60*60*24):
    print("new commit found!")
    print(delta)
    exit(0)
else:
    print("last commit too old")
    exit(1)
