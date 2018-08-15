import subprocess

tag_strs = subprocess.check_output(["git", "tag"])
tags = filter(None, tag_strs.split("\n"))
curr_version = int(tags[-1][1:])
new_version = str(curr_version + 1)
print ("Bump version " + str(curr_version) + " to " + new_version)

# Read in the file
filepath = "../app/build.gradle"
with open(filepath, 'r') as file :
  filedata = file.read()
# Replace the target string
new_filedata = filedata.replace('versionCode 1', "versionCode " + new_version)
new_filedata = new_filedata.replace('versionName "1"', 'versionName "' + new_version + '"')

# Write the file out again
with open(filepath, 'w') as file:
  file.write(new_filedata)

# Write backup for reseting version later
with open("./build.gradle.bck", 'w') as file:
  file.write(filedata)

# Tag version
tag = "v"+new_version
print ("Tagging current commit with "+tag)
print(subprocess.check_output(["git", "tag", tag]))
print(subprocess.check_output(["git", "push", "--tags"]))
