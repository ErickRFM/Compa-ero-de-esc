const http = require('http');
const PORT = process.env.PORT || 4500;

const BASE = '/api/integrations/companion/v1';

function sendJson(res, statusCode, payload) {
  res.writeHead(statusCode, { 'Content-Type': 'application/json' });
  res.end(JSON.stringify(payload));
}

const server = http.createServer((req, res) => {
  const { method } = req;
  // Strip query string for matching
  const path = (req.url || '').split('?')[0];

  // @endpoint GET /api/integrations/companion/v1/authority
  if (method === 'GET' && path === BASE + '/authority') {
    sendJson(res, 200, {
      authority: 'SIII-FAKE-AUTHORITY',
      institution: 'Fake Institutional Academic Provider',
      version: 'v1',
      issuedAt: '2026-01-01T00:00:00Z',
      capabilities: ['courses', 'teachers', 'students', 'academic-load']
    });
    return;
  }

  // @endpoint GET /api/integrations/companion/v1/courses
  if (method === 'GET' && path === BASE + '/courses') {
    sendJson(res, 200, [
      { id: 'CRS-001', code: 'MATH101', name: 'Fake Mathematics I', credits: 6 },
      { id: 'CRS-002', code: 'PHYS101', name: 'Fake Physics I', credits: 5 },
      { id: 'CRS-003', code: 'CS101', name: 'Fake Intro to Computing', credits: 4 }
    ]);
    return;
  }

  // @endpoint GET /api/integrations/companion/v1/teachers
  if (method === 'GET' && path === BASE + '/teachers') {
    sendJson(res, 200, [
      { id: 'TCH-001', name: 'Prof. Fake Alpha', email: 'alpha@fake-siii.test' },
      { id: 'TCH-002', name: 'Prof. Fake Beta', email: 'beta@fake-siii.test' }
    ]);
    return;
  }

  // @endpoint GET /api/integrations/companion/v1/students/{subject}/academic-load
  const loadMatch = path.match(new RegExp('^' + BASE + '/students/([^/]+)/academic-load$'));
  if (method === 'GET' && loadMatch) {
    const subject = decodeURIComponent(loadMatch[1]);
    sendJson(res, 200, {
      subject,
      authorized: true,
      totalCredits: 15,
      academicLoad: [
        { courseId: 'CRS-001', code: 'MATH101', status: 'AUTHORIZED', credits: 6 },
        { courseId: 'CRS-003', code: 'CS101', status: 'AUTHORIZED', credits: 4 },
        { courseId: 'CRS-002', code: 'PHYS101', status: 'PENDING', credits: 5 }
      ],
      generatedAt: '2026-01-01T00:00:00Z'
    });
    return;
  }

  // @endpoint GET /api/integrations/companion/v1/students/{subject}
  const studentMatch = path.match(new RegExp('^' + BASE + '/students/([^/]+)$'));
  if (method === 'GET' && studentMatch) {
    const subject = decodeURIComponent(studentMatch[1]);
    sendJson(res, 200, {
      subject,
      studentId: 'STU-FAKE-0001',
      name: 'Fake Student One',
      program: 'Fake Engineering Program',
      enrollmentStatus: 'ACTIVE'
    });
    return;
  }

  sendJson(res, 404, { error: 'Mock route not defined', method, path });
});

server.listen(PORT, () => console.log('SIII mock server running on port ' + PORT));
