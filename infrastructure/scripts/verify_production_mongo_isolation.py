#!/usr/bin/env python3
"""Read-only, secret-free preflight for Companion MongoDB production isolation.

Inputs are explicit metadata, never MongoDB connection strings. This command
cannot move data, inspect credentials, change accounts or authorize invitation
sending. Exit code 0 only means the supplied metadata satisfies this policy.
"""
import argparse
import json
import re
import sys
from pathlib import Path

DATABASE_RE = re.compile(r"^[A-Za-z0-9_]{3,64}$")
DISALLOWED_ROLES = {"atlasAdmin", "root", "readWriteAnyDatabase", "dbOwner", "userAdminAnyDatabase", "dbAdminAnyDatabase", "clusterAdmin"}


def assess(*, production_database, qa_database, development_database, principal=None, atlas_users=None):
    errors = []
    for label, db in (("production", production_database), ("qa", qa_database), ("development", development_database)):
        if not DATABASE_RE.fullmatch(db):
            errors.append(f"{label} database must be a simple 3-64 character logical MongoDB name")
    if production_database in {qa_database, development_database} or qa_database == development_database:
        errors.append("production, QA and development must have three distinct database names")
    if not production_database.endswith("_prod"):
        errors.append("production database must end in _prod (a dedicated namespace)")
    if not qa_database.endswith("_qa"):
        errors.append("QA database must end in _qa")
    if not development_database.endswith("_dev"):
        errors.append("development database must end in _dev")
    if not principal:
        errors.append("dedicated production database principal is required")
    if atlas_users is None:
        errors.append("Atlas database-user role evidence is required; namespace-only checks do not certify isolation")
    elif not isinstance(atlas_users, dict) or not isinstance(atlas_users.get("users"), list):
        errors.append("Atlas evidence must be a JSON object with a users array")
    elif principal:
        accounts = [account for account in atlas_users["users"] if account.get("username") == principal]
        if len(accounts) != 1:
            errors.append("dedicated production principal must exist exactly once in Atlas evidence")
        else:
            roles = accounts[0].get("roles")
            if not isinstance(roles, list):
                errors.append("production principal must have an explicit roles array")
            else:
                has_rw = False
                for entry in roles:
                    if not isinstance(entry, dict):
                        errors.append("invalid Atlas role entry")
                        continue
                    role = entry.get("roleName")
                    database = entry.get("databaseName")
                    if role in DISALLOWED_ROLES:
                        errors.append("production principal has an overprivileged Atlas role")
                    if role == "readWrite" and database == production_database:
                        has_rw = True
                    elif role == "readWrite" and database != production_database:
                        errors.append("production principal can write outside its production database")
                    elif role != "readWrite":
                        errors.append("production principal has an unexpected role; require only readWrite on its own database")
                if not has_rw:
                    errors.append("production principal lacks readWrite on its exclusive database")
    return {"passed": not errors, "errors": sorted(set(errors)), "checks": {"distinct_database_names": len({production_database, qa_database, development_database}) == 3, "role_evidence_present": atlas_users is not None, "principal_provided": bool(principal)}}


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--production-database", required=True)
    parser.add_argument("--qa-database", required=True)
    parser.add_argument("--development-database", required=True)
    parser.add_argument("--production-principal", required=True)
    parser.add_argument("--atlas-users-json", type=Path, required=True, help="Redacted JSON from Atlas list of DB users; NO passwords or connection strings")
    args = parser.parse_args(argv)
    try:
        evidence = json.loads(args.atlas_users_json.read_text(encoding="utf-8"))
    except (ValueError, OSError):
        print(json.dumps({"passed": False, "errors": ["Cannot read non-secret Atlas role evidence"], "checks": {}}))
        return 2
    result = assess(production_database=args.production_database, qa_database=args.qa_database,
                    development_database=args.development_database, principal=args.production_principal,
                    atlas_users=evidence)
    print(json.dumps(result, sort_keys=True))
    return 0 if result["passed"] else 2


if __name__ == "__main__":
    sys.exit(main())
