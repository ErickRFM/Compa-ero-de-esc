"""Exercise existing mock identities through JWT API. Fixed localhost only."""
import json
import pathlib
import urllib.request
import urllib.error
from datetime import date

BASE = 'http://127.0.0.1:8080'

def request(path, token=None, data=None):
    req = urllib.request.Request(BASE + path, data=json.dumps(data).encode() if data is not None else None,
        headers={'Content-Type': 'application/json', **({'Authorization': 'Bearer ' + token} if token else {})})
    with urllib.request.urlopen(req, timeout=20) as response:
        return json.load(response)['data']

def login(name, password):
    result = request('/auth/login', data={'username': name, 'password': password})
    return result['accessToken']

teacher = login('elena.rios', 'development-only-teacher')
student = login('ana.lopez', 'development-only')
channels = request('/channels', teacher)
evidence = {'environment': 'LOCAL QA ONLY; existing development identities; in-memory data', 'channels': [], 'student_publish_status': None}
for channel in channels:
    post = request('/channels/' + channel['id'] + '/posts', teacher, {
        'type': 'announcement', 'title': 'QA: aviso de clase', 'body': 'Fixture local de auditoría. Revisa el material y utiliza una respuesta permitida.',
        'allowedResponses': ['acknowledged', 'confirmed', 'need_clarification'], 'pinned': True,
    })
    evidence['channels'].append({'id': channel['id'], 'subjectName': channel['subjectName'], 'postId': post['id']})
try:
    request('/channels/' + channels[0]['id'] + '/posts', student, {'body': 'QA forbidden publish'})
    evidence['student_publish_status'] = 'UNEXPECTED SUCCESS'
except urllib.error.HTTPError as error:
    evidence['student_publish_status'] = error.code
today = date.today().isoformat()
schedule = request('/academic/schedule/v2?weekOf=' + today, teacher)
occurrences = [item for item in schedule['occurrences'] if item['date'] == today]
if occurrences:
    occurrence = occurrences[0]
    try:
        session = request('/attendance/sessions', teacher, {'occurrenceId': occurrence['id'], 'occurrenceDate': occurrence['date'], 'durationMinutes': 15})
        evidence['session'] = session
    except urllib.error.HTTPError as error:
        evidence['session_failure'] = {'status': error.code, 'body': error.read().decode()}
path = pathlib.Path(__file__).resolve().parents[2] / 'docs/audits/v8-evidence/logs/local-fixtures.json'
path.parent.mkdir(parents=True, exist_ok=True)
path.write_text(json.dumps(evidence, ensure_ascii=False, indent=2), encoding='utf-8')
print(json.dumps(evidence, ensure_ascii=True, indent=2))
