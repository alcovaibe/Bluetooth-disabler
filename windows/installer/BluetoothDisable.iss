#ifndef PayloadDir
  #error PayloadDir must point at the self-contained publish output.
#endif
#ifndef OutputDir
  #error OutputDir must be provided.
#endif
#ifndef AppVersion
  #error AppVersion must be provided.
#endif

[Setup]
AppId={{28D89097-25E5-465E-99E4-07DD9F74BDB0}
AppName=Bluetooth Disable
AppVersion={#AppVersion}
AppPublisher=Bluetooth Disable contributors
AppPublisherURL=https://github.com/alcovaibe/Bluetooth-disabler
AppSupportURL=https://github.com/alcovaibe/Bluetooth-disabler/issues
DefaultDirName={localappdata}\Programs\Bluetooth Disable
DefaultGroupName=Bluetooth Disable
DisableProgramGroupPage=yes
PrivilegesRequired=lowest
ArchitecturesAllowed=x64os
ArchitecturesInstallIn64BitMode=x64os
MinVersion=10.0.19041
OutputDir={#OutputDir}
OutputBaseFilename=BluetoothDisable-{#AppVersion}-win-x64-setup
SetupIconFile=..\BluetoothDisable.App\Assets\AppIcon.ico
UninstallDisplayIcon={app}\BluetoothDisable.App.exe
LicenseFile=..\..\LICENSE
Compression=lzma2
SolidCompression=yes
WizardStyle=modern
CloseApplications=yes
RestartApplications=no
SetupLogging=yes
VersionInfoVersion={#AppVersion}.0
VersionInfoDescription=Bluetooth Disable preview installer
UninstallDisplayName=Bluetooth Disable

[Languages]
Name: "russian"; MessagesFile: "compiler:Languages\Russian.isl"
Name: "english"; MessagesFile: "compiler:Default.isl"

[Files]
Source: "{#PayloadDir}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{group}\Bluetooth Disable"; Filename: "{app}\BluetoothDisable.App.exe"; WorkingDir: "{app}"
Name: "{group}\Uninstall Bluetooth Disable"; Filename: "{uninstallexe}"