#!/usr/bin/env python3
"""Forced SSH command installed alongside the trusted stand Compose configuration."""

import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tarfile
import tempfile


def receive():
    command = re.fullmatch(r"deploy ([0-9a-f]{40}) ([1-9][0-9]*)",
                           os.environ.get("SSH_ORIGINAL_COMMAND", ""))
    if not command:
        sys.exit("Expected deploy <commit SHA> <workflow run number>")
    sha, number = command.groups()
    expected = {f"backend-{sha}.tar.gz", f"frontend-{sha}.tar.gz"}
    with tempfile.TemporaryDirectory() as staging:
        with tarfile.open(fileobj=sys.stdin.buffer, mode="r|") as stream:
            for member in stream:
                if not member.isfile() or member.name not in expected:
                    sys.exit("Unexpected release archive member")
                expected.remove(member.name)
                with open(Path(staging) / member.name, "xb") as output:
                    shutil.copyfileobj(stream.extractfile(member), output)
        if expected:
            sys.exit("Incomplete release archive")
        script = Path(__file__).resolve().with_name("deploy.sh")
        subprocess.run(["bash", script, sha, number, staging], check=True)


if __name__ == "__main__":
    receive()
