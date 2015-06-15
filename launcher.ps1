#!/usr/bin/env powershell
<#####################################################################################
# launcher.ps1
#
#
######################################################################################>

$__Author__="Force.Charlie"
$__Date__="2015.05.25"
$PrefixDir=Split-Path -Parent $MyInvocation.MyCommand.Definition

###Get Registry Value
Function Get-RegistryValue
{
    param(
        [Parameters(Position=0,Mandatory=$True,HelpMessage="Enter Key")]
        [ValidateNotNullorEmpty()]
        [String]$Key,
        [Parameter(Position=1,Mandatory=$True,HelpMessage="Enter Sub key")]
        [ValidateNotNullorEmpty()]
        [String]$Subkey
        )
    (Get-ItemProperty $Key $Subkey).$value
}

Function Parser-IniFile
{
    param(
        [Parameter(Position=0,Mandatory=$True,HelpMessage="Enter Your Ini File Path")]
        [ValidateNotNullorEmpty()]
        [String]$File
        )
    $ini = @{}
    $section = "NO_SECTION"
    $ini[$section] = @{}
    switch -regex -file $File {
        "^\[(.+)\]$" {
            $section = $matches[1].Trim()
            $ini[$section] = @{}
        }
        "^\s*([^#].+?)\s*=\s*(.*)" {
            $name,$value = $matches[1..2]
            # skip comments that start with semicolon:
            if (!($name.StartsWith(";"))) {
                $ini[$section][$name] = $value.Trim()
            }
        }
    }
    $ini
}

Function Get-VMOptions
{
        param(
        [Parameter(Position=0,Mandatory=$True,HelpMessage="Enter Your Java VMOptions Configure file Path:")]
        [ValidateNotNullorEmpty()]
        [String]$File
        )
        $vmStr=$null
        $content=Get-Content $File
        foreach( $line in $content){
            if($line[0] -ne '#')
            {
                $vmStr="${vmStr} $line"
            }
        }
        $vmStr
}

Function Get-JavaSE
{
    $jdk=$env:JAVA_HOME
    #This is regedit search java
    return $jdk
}

Function Get-InsiderProcessId(){
    #Process Id
    if((Test-Path "${PrefixDir}\launcher.lock.pid") -ne $True){
      return 0
    }
    $IdValue=Get-Content "${PrefixDir}\launcher.lock.pid"
    $id=$IdValue
    return $id
}

Function Stop-InsiderService(){
    $javaid=Get-InsiderProcessId
    IF($javaid -eq 0)
    {
        $Obj=Get-Process -Name "Java"
        if( $Obj -ne $null){
            #
            Write-Host "Not found any java process in your system."
            return 1
        }
    }
    $Obj=Get-Process -Id $javaid
    if($Obj -ne $null){
        Stop-Process -Force -Id $javaid
        Write-Host "Wait Stop Java Service"
        Sleep 3
        $Obj2=Get-Process -Id $javaid
        if($Obj2 -ne $null){
            Write-Host "Stop Java Service Failed !"
            return 3
        }
        Remove-Item -Path "${PrefixDir}/launcher.lock.pid"
        Write-Host "Stop Java Service Success !"
        return 0
    }
}


Function Print-HelpMessage(){
    Write-Host "Service launcher shell
usage: launcher Option -Trace
`t-Start`t`tStart service
`t-Stop`t`tStop service
`t-Restart`tRestart service
`t-Status`t`tGet service run status
`t-Help`t`tPrint usage and exit
`t-Trace`t`tTrace output,not set redirect standard io"
    Write-Host "Author:$__Author__, Date: $__Date__"
}



Write-Host  "Service Launcher `nPlease Set launcher.cfg configure Redirect output and
Set launcher.vmoptions ,get jvm startup paramteres"

$cmd = $args


$TheseIni="${PrefixDir}/launcher.cfg"
$Thesevmo="${PrefixDir}/launcher.vmoptions"

#Start-Process
#Stop-Process [-id]
#$pro = Get-Process -name java; $pro.Kill();

IF($cmd -icontains "-help")
{
    Print-HelpMessage
    exit 0
}

IF($cmd -icontains "-status"){
    $javaid=Get-InsiderProcessId
    $Obj=Get-Process -Id $javaid
    if($Obj -eq $null){
        Write-Host "Not Found Bind Service is running!"
        Remove-Item -Path "${PrefixDir}/launcher.lock.pid"
        exit 1
    }
    if($Obj.ProcessName -eq "Java"){
        Write-Host "Process Info:`n${Obj}"
    }else{
        Write-Host "From Process Id find ProcessName,but this name is not java"
        Remove-Item -Path "${PrefixDir}/launcher.lock.pid"
        exit 1
    }
    exit 0
}

IF($cmd -icontains "-stop"){
    Stop-InsiderService
    exit 0
}


IF($cmd -icontains "-restart"){
    Stop-InsiderService
    #Stop and not exit
}
$StdoutFile="Debug.log"
$IniAttr=Parser-IniFile -File "${PrefixDir}/launcher.cfg"
$VMOptions=Get-VMOptions -File "${PrefixDir}/launcher.vmoptions"
$StdoutFile=$IniAttr["Windows"]["stdout"]
$StderroFile=$IniAttr["Windows"]["stderr"]
$JdkRawEnv=$IniAttr["Windows"]["JAVA_HOME"]
$AppPackage=$IniAttr["Environment"]["Package"]
######Parser-IniFile Support Spaces
$Parameters=$IniAttr["Environment"]["Params"]

$oldid=Get-InsiderProcessId
IF($oldid -ne 0){
    $TaskObj = Get-Process -id $oldid
    IF($TaskObj -ne $null -and $TaskObj.name -eq "Java"){
        Write-Host "Failed start Service,${PrefixDir}:${AppPackage} alway runing !"
        exit 1
    }
}

$JavaEnv="$env:JAVA_HOME"
IF($JdkRawEnv -eq $null)
{
    $JavaEnv=Get-JavaSE
}else{
    $JavaEnv=$JdkRawEnv
}

$JavaExe="java"
IF($JavaEnv -ne $null)
{
    $JavaExe="${JavaEnv}/bin/java.exe"
}


IF($JavaEnv -eq $null ){
    Write-Host "Not Found any Java JDK in your setting or in your environment!"
    Exit 1
}
####By default
IF($Trace){
    Start-Process -FilePath "${JavaExe}" -Argumentlist "${VMOptions} -jar ${PrefixDir}\${AppPackage} $Parameters" 
}else{
   $ProcessObj= Start-Process -FilePath "${JavaExe}" -PassThru -Argumentlist "${VMOptions} -jar ${PrefixDir}\${AppPackage} $Parameters"  -RedirectStandardOutput "${StdoutFile}" -RedirectStandardError "${StdoutFile}" -WindowStyle Hidden
   IF( $ProcessObj -eq $null){
    Write-Host "Failed to start Java Service: Package Name: ${AppPackage}"
    Write-Host "CurrentDir: ${PrefixDir}"
    Write-Host "JavaPath: ${JavaExe}"
    Write-Host "VMOptions: ${VMOptions}"
    Write-Host "Stdio: ${StdoutFile}"
    Write-Host "Your can find error info from ${StderroFile}"
    exit 1
   }
   $InPid=$ProcessObj.Id
   $ProcessObj.Id | Out-File $PrefixDir/launcher.lock.pid
   Write-Host "Success ,Your can find log from: ${StdoutFile}.`nView the service status type: launcher Status "
}
