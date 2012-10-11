@ECHO OFF

IF [BROWSER]==[%1] GOTO _BROWSER {Subroutine-Handler}

echo Browser will start in 10 seconds...
start /B "CMD window" %0 BROWSER

cd /d "%~dp0"
java -Dconfig.file="%~dp0/application.conf" %* -cp "%~dp0\lib\*;" play.core.server.NettyServer %~dp0

GOTO EOF {=Subroutine-section-below=}
:_BROWSER

timeout 10
start http://localhost:9000/

:EOF {End-of-file}
