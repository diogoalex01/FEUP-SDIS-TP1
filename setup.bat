REM # SDIS TP1 : Setup batch file

@ECHO OFF
IF "%~1"=="" (
    GOTO ERROR
)

IF "%~2"=="" (
    GOTO ERROR
)

IF "%~3"=="-reset" (
    set CURRENTDIR = %cd%
    FOR /F "DELIMS==" %%d in ('DIR "%CURRENTDIR%" /AD /B') DO (
        rmdir %%d /s /q
    )
) ELSE IF NOT "%~3"=="" (
    GOTO ERROR
)

javac *.java
start "RMI Registry" rmiregistry
set /A number_of_peers=%1

FOR /L %%i IN (1, 1, %number_of_peers%) DO (
    start "Peer %%i - Version %2" java Peer %2 %%i AP%%i 230.0.0.0 4445 231.0.0.0 4446 232.0.0.0 4447
)

start "Test Client Window"

GOTO END

:ERROR
    ECHO Usage: ./setup.bat number_of_peers protocol_version [-reset]
    ECHO E.g.:  ./setup.bat 3 1.0

:END
