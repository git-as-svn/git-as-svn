#!/usr/bin/env powershell
<#####################################################################################
# git-as-svn
#
#
######################################################################################>

__Author__="Force.Charlie"
__Date__="2015.05.25"
$PrefixDir=Split-Path -Parent $MyInvocation.MyCommand.Definition

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

Function Get-VMOptions(){
        param(
        [Parameter(Position=0,Mandatory=$True,HelpMessage="Enter Your Java VMOptions Configure file Path:")]
        [ValidateNotNullorEmpty()]
        [String]$File
        )
        $vmmap=@{}
        $vmmap
}

Function Get-JavaSE(){
    param(
        [Parameters(Position=0)]
        [String]$Default
        )
    $jdk=""
    $jdk
}

Function Print-HelpMessage(){
    Write-Host "git-as-svn launcher shell
    usage: launcher -Option [Value] -Help -Trace"
}



Write-Host  "git-as-svn Launcher `n Please Set launcher.cfg configure Redirect output and
Set launcher.vmoptions ,get jvm startup paramteres"

Param(
    [Parameters(Position=0,Mandatory=$True,HelpMessage="Input your options: [start |restart |stop |status]")]
    [ValidateNotNullorEmpty()]
    [String]$Option
    [switch]$Help
    [switch]$Trace
    )

$TheseIni="${PrefixDir}/launcher.cfg"
$Thesevmo="${PrefixDir}/launcher.vmoptions"

#Start-Process
#Stop-Process [-id] 
#$pro = Get-Process -name java; $pro.Kill(); 

IF($)

Start-Process -FilePath "${JavaEnv}/bin/java.exe" -Argumentlist "${VMOptions} -jar ${PrefixDir}\git-as-svn.jar $Parameters"  `
-RedirectStandardOutput "${RedirectFile}" -RedirectStandardError "${RedirectFile}" -WindowStyle Hidden
