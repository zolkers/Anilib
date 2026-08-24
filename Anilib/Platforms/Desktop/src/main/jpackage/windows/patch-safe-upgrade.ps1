param(
    [Parameter(Mandatory = $true)]
    [string] $MsiPath,

    [Parameter(Mandatory = $true)]
    [string] $MigrationScriptPath
)

$ErrorActionPreference = 'Stop'
$resolvedMsi = (Resolve-Path -LiteralPath $MsiPath -ErrorAction Stop).Path
if ([IO.Path]::GetExtension($resolvedMsi) -ne '.msi') {
    throw "Refusing to patch a non-MSI file: $resolvedMsi"
}
$resolvedMigrationScript = (Resolve-Path -LiteralPath $MigrationScriptPath -ErrorAction Stop).Path

$installer = New-Object -ComObject WindowsInstaller.Installer
$database = $installer.GetType().InvokeMember(
    'OpenDatabase',
    'InvokeMethod',
    $null,
    $installer,
    @($resolvedMsi, 1)
)

function Invoke-MsiSql {
    param([Parameter(Mandatory = $true)][string] $Sql)
    $view = $database.GetType().InvokeMember('OpenView', 'InvokeMethod', $null, $database, @($Sql))
    try {
        $view.GetType().InvokeMember('Execute', 'InvokeMethod', $null, $view, $null) | Out-Null
    } finally {
        try {
            $view.GetType().InvokeMember('Close', 'InvokeMethod', $null, $view, $null) | Out-Null
        } catch {
            # The database commit below remains the authoritative operation.
        }
    }
}

Invoke-MsiSql "DELETE FROM ``InstallExecuteSequence`` WHERE ``Action``='JpMigrateLegacyData'"
Invoke-MsiSql "DELETE FROM ``CustomAction`` WHERE ``Action``='JpMigrateLegacyData'"
Invoke-MsiSql "DELETE FROM ``Binary`` WHERE ``Name``='AnilibMigrateLegacyData'"

$binaryView = $database.GetType().InvokeMember(
    'OpenView',
    'InvokeMethod',
    $null,
    $database,
    @("INSERT INTO ``Binary`` (``Name``,``Data``) VALUES ('AnilibMigrateLegacyData', ?)")
)
try {
    $binaryRecord = $installer.GetType().InvokeMember('CreateRecord', 'InvokeMethod', $null, $installer, @(1))
    $binaryRecord.GetType().InvokeMember('SetStream', 'InvokeMethod', $null, $binaryRecord, @(1, $resolvedMigrationScript)) | Out-Null
    $binaryView.GetType().InvokeMember('Execute', 'InvokeMethod', $null, $binaryView, @($binaryRecord)) | Out-Null
} finally {
    try {
        $binaryView.GetType().InvokeMember('Close', 'InvokeMethod', $null, $binaryView, $null) | Out-Null
    } catch {
        # The database commit below remains the authoritative operation.
    }
}

Invoke-MsiSql ("INSERT INTO ``CustomAction`` (``Action``,``Type``,``Source``,``Target``) " +
        "VALUES ('JpMigrateLegacyData',6,'AnilibMigrateLegacyData','MigrateLegacyData')")
Invoke-MsiSql ("INSERT INTO ``InstallExecuteSequence`` (``Action``,``Condition``,``Sequence``) " +
        "VALUES ('JpMigrateLegacyData','NOT REMOVE',1440)")
Invoke-MsiSql "UPDATE ``InstallExecuteSequence`` SET ``Sequence``=1450 WHERE ``Action``='RemoveExistingProducts'"

$database.GetType().InvokeMember('Commit', 'InvokeMethod', $null, $database, $null) | Out-Null
