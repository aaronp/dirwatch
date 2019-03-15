This project runs a basic directory watch script to periodically poll a directory and provide the diffs as a stream
of files.

The 'UploadService' then interprets some '<id>.ready' file to indicate that all files which match the <id>__* pattern
(configurable) should be moved to an <id> directory (excluding the <id>__ part), then run a 'run.sh' script.

If the <id>.ready file is non-empty, then its contents are interpreted to be the name of the script to run.
e.g. if we put a file called 'dothis__runThisScript.sh' into the upload directory, followed by a 'dothis.ready' file
whose contents are 'runThisScript.sh', then 'exec/dothis/runThisScript.sh' will be executed.

A full example running via docker:

```bash
# create a directory which will have files uploaded to it:
mkdir upload

# create a working directory which will have uploads executed
mkdir exec

# start watching the 'upload' directory
docker run --rm -it -v `pwd`/upload:/upload -v `pwd`/exec:/exec  porpoiseltd/dirwatch:latest

# test it -- create some files in 'upload'
echo "hello" > upload/test__hello.txt
echo " world" > upload/test__world.txt
echo "cat hello.txt world.txt" > upload/test__testRunScript.sh

# finally, send the 'test.ready' file which will signal the watcher that it should run
echo "testRunScript.sh" > upload/test.ready

# now the test* files will be removed from 'upload' by the watcher and put into 'exec/test/'
# and exec/test/testRunScript.sh will be run (because that script name is the contents of the
# .ready file). If just did a 'touch test.ready' then it would try to run the default script,
# which according to the default configuration is run.sh
```