Option Explicit

Const AliasAttribute = 1024

Dim fileSystem

Function MigrateLegacyData()
    Dim shell
    Dim localAppData
    Dim sourceRoot
    Dim targetRoot

    Set fileSystem = CreateObject("Scripting.FileSystemObject")
    Set shell = CreateObject("WScript.Shell")
    localAppData = shell.ExpandEnvironmentStrings("%LOCALAPPDATA%")
    sourceRoot = fileSystem.BuildPath(localAppData, "Anilib")
    targetRoot = fileSystem.BuildPath(localAppData, "AnilibData")

    If fileSystem.FolderExists(sourceRoot) Then
        EnsureFolder targetRoot
        CopyDataFolder fileSystem.GetFolder(sourceRoot), targetRoot, True
    End If
    MigrateLegacyData = 1
End Function

Sub EnsureFolder(path)
    Dim parentPath
    If fileSystem.FolderExists(path) Then
        Exit Sub
    End If
    parentPath = fileSystem.GetParentFolderName(path)
    If Len(parentPath) > 0 And Not fileSystem.FolderExists(parentPath) Then
        EnsureFolder parentPath
    End If
    fileSystem.CreateFolder path
End Sub

Sub CopyDataFolder(sourceFolder, destinationPath, isRoot)
    Dim sourceFile
    Dim sourceSubfolder
    Dim destinationFile
    Dim destinationSubfolder

    For Each sourceFile In sourceFolder.Files
        If Not (isRoot And IsProgramEntry(sourceFile.Name)) Then
            destinationFile = fileSystem.BuildPath(destinationPath, sourceFile.Name)
            If Not fileSystem.FileExists(destinationFile) Then
                sourceFile.Copy destinationFile, False
            ElseIf sourceFile.DateLastModified > fileSystem.GetFile(destinationFile).DateLastModified Then
                sourceFile.Copy destinationFile, True
            End If
        End If
    Next

    For Each sourceSubfolder In sourceFolder.SubFolders
        If Not (isRoot And IsProgramEntry(sourceSubfolder.Name)) Then
            If (sourceSubfolder.Attributes And AliasAttribute) = 0 Then
                destinationSubfolder = fileSystem.BuildPath(destinationPath, sourceSubfolder.Name)
                EnsureFolder destinationSubfolder
                CopyDataFolder sourceSubfolder, destinationSubfolder, False
            End If
        End If
    Next
End Sub

Function IsProgramEntry(name)
    Dim normalized
    normalized = LCase(name)
    IsProgramEntry = normalized = "app" Or normalized = "runtime" Or normalized = "anilib.exe"
End Function
