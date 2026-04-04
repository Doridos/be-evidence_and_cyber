#!/usr/bin/env python3
import argparse
import os
import sys

import winrm


def ps_escape(value: str) -> str:
    return value.replace("'", "''")


def build_install_script(package_url: str, staging_dir: str) -> str:
    escaped_package_url = ps_escape(package_url)
    escaped_staging_dir = ps_escape(staging_dir)

    return f"""
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
$packageUrl = '{escaped_package_url}'
$stagingDir = '{escaped_staging_dir}'
$zipPath = Join-Path $stagingDir 'agent-package.zip'

if (Test-Path $stagingDir) {{
    Remove-Item -Path $stagingDir -Recurse -Force
}}

New-Item -ItemType Directory -Force -Path $stagingDir | Out-Null
Invoke-WebRequest -Uri $packageUrl -OutFile $zipPath
Expand-Archive -Path $zipPath -DestinationPath $stagingDir -Force
Set-Location $stagingDir
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\\install-agent.ps1
"""


def main() -> int:
    parser = argparse.ArgumentParser(description="Deploy the Windows agent over WinRM.")
    parser.add_argument("--target-host", required=True)
    parser.add_argument("--username", required=True)
    parser.add_argument("--package-url", required=True)
    parser.add_argument("--staging-dir", required=True)
    parser.add_argument("--scheme", default="http")
    parser.add_argument("--port", default="5985")
    parser.add_argument("--transport", default="ntlm")
    parser.add_argument("--server-cert-validation", default="ignore")
    args = parser.parse_args()

    password = os.environ.get("DEPLOY_AGENT_PASSWORD", "")
    if not password:
        print("Deployment password was not provided in DEPLOY_AGENT_PASSWORD.", file=sys.stderr)
        return 2

    endpoint = f"{args.scheme}://{args.target_host}:{args.port}/wsman"
    session = winrm.Session(
        target=endpoint,
        auth=(args.username, password),
        transport=args.transport,
        server_cert_validation=args.server_cert_validation,
    )

    result = session.run_ps(build_install_script(args.package_url, args.staging_dir))

    if result.std_out:
        print(result.std_out.decode("utf-8", errors="ignore").strip())
    if result.std_err:
        print(result.std_err.decode("utf-8", errors="ignore").strip(), file=sys.stderr)

    return int(result.status_code)


if __name__ == "__main__":
    sys.exit(main())
