"""Policy regression tests. No production DB connection or credentials are used."""
import importlib.util
import json
import pathlib
import tempfile
import unittest

SCRIPT = pathlib.Path(__file__).resolve().parents[1] / "verify_production_mongo_isolation.py"
SPEC = importlib.util.spec_from_file_location("verify_production_mongo_isolation", SCRIPT)
module = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(module)


class MongoIsolationPreflightTest(unittest.TestCase):
    def check(self, **kwargs):
        principal = kwargs.pop("principal", "companero_prod_rw")
        users = kwargs.pop("atlas_users", {"users": [{"username": principal, "roles": [{"roleName": "readWrite", "databaseName": "companero_prod"}]}]})
        data = {"production_database": "companero_prod", "qa_database": "companero_qa", "development_database": "companero_dev", "principal": principal, "atlas_users": users}
        data.update(kwargs)
        return module.assess(**data)

    def test_dedicated_production_principal_passes(self):
        self.assertTrue(self.check()["passed"])

    def test_current_live_production_database_is_rejected(self):
        result = self.check(production_database="companero_dev")
        self.assertFalse(result["passed"])
        self.assertTrue(any("three distinct" in msg for msg in result["errors"]))

    def test_cannot_reuse_qa_database(self):
        self.assertFalse(self.check(production_database="companero_qa")["passed"])

    def test_qa_and_dev_names_require_expected_environment(self):
        self.assertFalse(self.check(qa_database="companero_testing")["passed"])
        self.assertFalse(self.check(development_database="companero_live")["passed"])

    def test_legacy_atlas_admin_is_not_a_production_principal(self):
        users = {"users": [{"username": "legacy", "roles": [{"roleName": "atlasAdmin", "databaseName": "admin"}]}]}
        self.assertFalse(self.check(principal="legacy", atlas_users=users)["passed"])

    def test_cross_database_readwrite_is_not_allowed(self):
        users = {"users": [{"username": "companero_prod_rw", "roles": [{"roleName": "readWrite", "databaseName": "companero_prod"}, {"roleName": "readWrite", "databaseName": "companero_qa"}]}]}
        self.assertFalse(self.check(atlas_users=users)["passed"])

    def test_missing_role_evidence_fails_closed(self):
        self.assertFalse(self.check(atlas_users=None)["passed"])

    def test_unknown_database_user_fails(self):
        users = {"users": [{"username": "other-principal", "roles": [{"roleName": "readWrite", "databaseName": "companero_prod"}]}]}
        self.assertFalse(self.check(principal="not-listed", atlas_users=users)["passed"])

    def test_cli_uses_only_metadata_and_returns_nonzero_on_failure(self):
        with tempfile.TemporaryDirectory() as d:
            path = pathlib.Path(d) / "roles.json"
            path.write_text(json.dumps({"users": [{"username": "only-prod", "roles": [{"roleName": "readWrite", "databaseName": "companero_prod"}]}]}), encoding="utf-8")
            args = ["--production-database", "companero_prod", "--qa-database", "companero_qa", "--development-database", "companero_dev", "--production-principal", "only-prod", "--atlas-users-json", str(path)]
            self.assertEqual(module.main(args), 0)
            args[1] = "companero_dev"
            self.assertEqual(module.main(args), 2)


if __name__ == "__main__":
    unittest.main()
