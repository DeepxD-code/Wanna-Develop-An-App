@REM Maven Wrapper for Windows
@REM Auto-downloads Maven if not present
@echo off
setlocal
set MAVEN_WRAPPER_JAR="%USERPROFILE%\.m2\wrapper\dists\maven-wrapper.jar"
set DOWNLOAD_URL=https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.3.2/maven-wrapper-3.3.2.jar

if not exist %MAVEN_WRAPPER_JAR% (
    echo Downloading Maven Wrapper...
    powershell -Command "Invoke-WebRequest -Uri '%DOWNLOAD_URL%' -OutFile %MAVEN_WRAPPER_JAR%"
)

java -cp %MAVEN_WRAPPER_JAR% org.apache.maven.wrapper.MavenWrapperMain %*
endlocal
