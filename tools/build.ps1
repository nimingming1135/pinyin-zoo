#!/usr/bin/env pwsh
# 构建脚本：使用项目内置的 JDK17 + Gradle 8.7，无需系统环境变量
$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$env:JAVA_HOME = Join-Path $root ".tools\jdk17"
$gradle = Join-Path $root ".tools\gradle-8.7\bin\gradle.bat"

if (-not (Test-Path $gradle)) {
    Write-Error "找不到 Gradle: $gradle"
    exit 1
}

Write-Host "JAVA_HOME = $env:JAVA_HOME"
Write-Host "Gradle    = $gradle"

& $gradle --project-dir $root @args
exit $LASTEXITCODE
