#git-as-svn launcher 


###Windows
launcher.bat start launcher.ps1     
launcher.ps1 server start,stop restart status feature     
base powershell      

###Linux
launcher server start stop restart status feature,    
base bash-shell       



##launcher.cfg
ini-style configure file    
Section:      
Environment      
Package,git-as-svn.jar ,a jar package       
Params jar startup params ,can empty       

Section:        
Posix, setting on linux      
Windows,setting on windows      
Key: JAVA_HOME where jdk root       
Key: stdout,set standrand out file       

##launcher.vmoptions
Java jvm startup params (like IntelliJ IDEA on Windows jvm.vmoptions)



