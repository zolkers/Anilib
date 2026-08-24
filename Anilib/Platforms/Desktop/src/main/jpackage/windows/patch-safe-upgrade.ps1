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

function Enable-SameVersionMajorUpgrade {
    $sql = "SELECT ``UpgradeCode``, ``VersionMin``, ``VersionMax``, ``Language``, ``Attributes``, ``Remove``, ``ActionProperty`` FROM ``Upgrade`` WHERE ``ActionProperty``='JP_UPGRADABLE_FOUND'"
    $view = $database.GetType().InvokeMember('OpenView', 'InvokeMethod', $null, $database, @($sql))
    try {
        $view.GetType().InvokeMember('Execute', 'InvokeMethod', $null, $view, $null) | Out-Null
        $record = $view.GetType().InvokeMember('Fetch', 'InvokeMethod', $null, $view, $null)
        if ($null -eq $record) {
            throw 'MSI has no JP_UPGRADABLE_FOUND row'
        }
        $attributes = [int]$record.GetType().InvokeMember(
            'IntegerData', 'GetProperty', $null, $record, 5)
        # Attributes participates in the Upgrade table key, so MSI cannot
        # update it in place. Replace the row inside the same transaction.
        $view.GetType().InvokeMember('Modify', 'InvokeMethod', $null, $view, @(6, $record)) | Out-Null
        $record.GetType().InvokeMember(
            'IntegerData', 'SetProperty', $null, $record, @(5, [int]($attributes -bor 512))) | Out-Null
        $view.GetType().InvokeMember('Modify', 'InvokeMethod', $null, $view, @(1, $record)) | Out-Null
    } finally {
        try {
            $view.GetType().InvokeMember('Close', 'InvokeMethod', $null, $view, $null) | Out-Null
        } catch {
            # The database commit below remains the authoritative operation.
        }
    }
}

# jpackage derives ProductCode from ProductVersion. Rebuilding the same version
# otherwise enters maintenance mode and leaves the previously installed files
# untouched. A fresh ProductCode plus an inclusive upper upgrade bound makes a
# rebuilt package a real same-version major upgrade while UpgradeCode remains
# stable across releases.
$newProductCode = [Guid]::NewGuid().ToString('B').ToUpperInvariant()
Invoke-MsiSql "UPDATE ``Property`` SET ``Value``='$newProductCode' WHERE ``Property``='ProductCode'"
Enable-SameVersionMajorUpgrade

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
