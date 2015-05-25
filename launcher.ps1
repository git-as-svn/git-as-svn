#!/usr/bin/env powershell
<#####################################################################################
# git-as-svn
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
    param(
        [String]$Default
        )
    if($Default -eq $null){

        }else{
            $jdk=$env:JAVA_HOME
        }
    $jdk=""
    $jdk
}

Function Get-ProcessId(){
    #Process Id
    if(!Test-Path "${PrefixDir}\launcher.lock.pid"){
      return 0
    }
    $IdValue=Get-Content "${PrefixDir}\launcher.lock.pid"
    $id=$IdValue[0]
    $id
}

Function Print-HelpMessage(){
    Write-Host "git-as-svn launcher shell
    usage: launcher Option -Trace
    `t-Start`tStart git-as-svn
    `t-Stop`tStop git-as-svn
    `t-Restart`tRestart git-as-svn
    `t-Status`tGet git-as-svn run status
    `t-Help`tPrint usage and exit
    `t-Trace`tTrace output,not set redirect standard io"
}



Write-Host  "git-as-svn Launcher `n Please Set launcher.cfg configure Redirect output and
Set launcher.vmoptions ,get jvm startup paramteres"

Param(
    [switch]$Stop,
    [switch]$Restart,
    [switch]$Status,
    [switch]$Start,
    [switch]$Help,
    [switch]$Trace
    )

$TheseIni="${PrefixDir}/launcher.cfg"
$Thesevmo="${PrefixDir}/launcher.vmoptions"

#Start-Process
#Stop-Process [-id] 
#$pro = Get-Process -name java; $pro.Kill(); 

IF($Help)
{
    Print-HelpMessage
    exit 0
}

IF($Status){
    exit 0;
}

IF($Stop){
    exit 0;
}

IF($Restart){
    #Stop and not exit
}

$IniAttr=Parser-IniFile -File "${PrefixDir}/launcher.cfg"
$VMOptions=Get-VMOptions -File "${PrefixDir}/launcher.vmoptions"
$RedirectFile=$IniAttr["Windows"]["stdout"]
$JdkRawEnv=$IniAttr["Windows"]["JAVA_HOME"]
$AppPackage=$IniAttr["Environment"]["Package"]
if
$JavaEnv=Get-JavaSE -Default $JdkRawEnv

IF(! Test-Path $JavaEnv ){
    Write-Host "Not Found any Java JDK in your setting or in your environment!"
    Exit 1
}
####By default
IF($Trace){
    Start-Process -FilePath "${JavaEnv}/bin/java.exe" -Argumentlist "${VMOptions} -jar ${PrefixDir}\git-as-svn.jar $Parameters"  -WindowStyle Hidden
}else{
   $ProcessObj= Start-Process -FilePath "${JavaEnv}/bin/java.exe" -PassThru -Argumentlist "${VMOptions} -jar ${PrefixDir}\git-as-svn.jar $Parameters"  `
-RedirectStandardOutput "${RedirectFile}" -RedirectStandardError "${RedirectFile}" -WindowStyle Hidden
   $PId=$ProcessObj.Id
   $ProcessObj.Id | Out-File $PrefixDir/launcher.lock.pid
}
