/**
 * Dev-only mock of the Library REST API (the com.sun.net.httpserver backend).
 *
 * Lets you exercise and review web/index.html in a browser without MySQL/Maven:
 *
 *     node tools/mock-server.js          # http://localhost:8080
 *
 * It mirrors the real contract: paths, methods, status codes, response bodies,
 * Bearer-token auth (401 when missing/unknown) and validation errors.
 * Demo login: admin / admin123   ·   librarian / librarian123
 *
 * Everything is in-memory: restart to reset. Safe to delete — the real app
 * only needs `web/index.html` plus the Java backend.
 */
'use strict';

const http = require('http');
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

const PORT = process.env.PORT || 8080;
const INDEX = path.join(__dirname, '..', 'web', 'index.html');

/* ----------------------------------------------------------------- seed data */
const daysAgo = (n) => new Date(Date.now() - n * 86400000).toISOString().slice(0, 10);
const daysAhead = (n) => new Date(Date.now() + n * 86400000).toISOString().slice(0, 10);
const stamp = (n) => new Date(Date.now() - n * 3600000).toISOString().slice(0, 19).replace('T', ' ');

let books = [
  { id: 1, title: 'The Hobbit', author: 'J.R.R. Tolkien', isbn: '9780547928227', publishYear: 1937, genre: 'Fantasy', condition: 'Available', totalCopies: 5, availableCopies: 3 },
  { id: 2, title: 'Dune', author: 'Frank Herbert', isbn: '9780441013593', publishYear: 1965, genre: 'Sci-Fi', condition: 'Available', totalCopies: 3, availableCopies: 2 },
  { id: 3, title: 'Pride and Prejudice', author: 'Jane Austen', isbn: '9780141439518', publishYear: 1813, genre: 'Classic', condition: 'Available', totalCopies: 2, availableCopies: 2 },
  { id: 4, title: 'Sapiens', author: 'Yuval Noah Harari', isbn: '9780062316097', publishYear: 2011, genre: 'History', condition: 'Available', totalCopies: 4, availableCopies: 4 },
  { id: 5, title: 'The Silent Patient', author: 'Alex Michaelides', isbn: '9781250301697', publishYear: 2019, genre: 'Thriller', condition: 'Damaged', totalCopies: 2, availableCopies: 1 },
  { id: 6, title: 'Educated', author: 'Tara Westover', isbn: '9780399590504', publishYear: 2018, genre: 'Memoir', condition: 'Available', totalCopies: 3, availableCopies: 2 },
  { id: 7, title: 'Clean Code', author: 'Robert C. Martin', isbn: '9780132350884', publishYear: 2008, genre: 'Technology', condition: 'Available', totalCopies: 2, availableCopies: 2 },
  { id: 8, title: 'Beloved', author: 'Toni Morrison', isbn: '9781400033416', publishYear: 1987, genre: 'Classic', condition: 'Lost', totalCopies: 1, availableCopies: 0 }
];

let members = [
  { id: 1, name: 'Amara Okafor', email: 'amara@example.com', phone: '+1 555 0101', activeStatus: true },
  { id: 2, name: 'Daniel Chen', email: 'daniel@example.com', phone: '+1 555 0102', activeStatus: true },
  { id: 3, name: 'Sofia Marino', email: 'sofia@example.com', phone: '+1 555 0103', activeStatus: true },
  { id: 4, name: 'Noah Bennett', email: 'noah@example.com', phone: null, activeStatus: true },
  { id: 5, name: 'Priya Raman', email: 'priya@example.com', phone: '+1 555 0105', activeStatus: false },
  { id: 6, name: 'Liam O’Connor', email: null, phone: '+1 555 0106', activeStatus: true }
];

let issues = [
  { id: 1, bookId: 1, memberId: 2, bookTitle: 'The Hobbit', memberName: 'Daniel Chen', issueDate: daysAgo(30), dueDate: daysAgo(16), returnDate: null, status: 'ISSUED', fineAmount: 0 },
  { id: 2, bookId: 2, memberId: 1, bookTitle: 'Dune', memberName: 'Amara Okafor', issueDate: daysAgo(20), dueDate: daysAgo(6), returnDate: null, status: 'ISSUED', fineAmount: 0 },
  { id: 3, bookId: 6, memberId: 3, bookTitle: 'Educated', memberName: 'Sofia Marino', issueDate: daysAgo(4), dueDate: daysAhead(10), returnDate: null, status: 'ISSUED', fineAmount: 0 },
  { id: 4, bookId: 5, memberId: 4, bookTitle: 'The Silent Patient', memberName: 'Noah Bennett', issueDate: daysAgo(25), dueDate: daysAgo(11), returnDate: daysAgo(9), status: 'RETURNED', fineAmount: 2.5 },
  { id: 5, bookId: 3, memberId: 6, bookTitle: 'Pride and Prejudice', memberName: 'Liam O’Connor', issueDate: daysAgo(2), dueDate: daysAhead(12), returnDate: null, status: 'ISSUED', fineAmount: 0 }
];

let logs = [
  { id: 1, librarianId: 1, librarianName: 'System Administrator', action: 'LOGIN', details: 'Logged in', timestamp: stamp(1) },
  { id: 2, librarianId: 1, librarianName: 'System Administrator', action: 'ISSUE_BOOK', details: 'Book id 3 (Pride and Prejudice) to Liam O’Connor', timestamp: stamp(2) },
  { id: 3, librarianId: 2, librarianName: 'Library Staff', action: 'ADD_BOOK', details: '"Sapiens" by Yuval Noah Harari', timestamp: stamp(5) },
  { id: 4, librarianId: 2, librarianName: 'Library Staff', action: 'RETURN_BOOK', details: 'Record id 4 (The Silent Patient), fine $2.50', timestamp: stamp(9) },
  { id: 5, librarianId: 2, librarianName: 'Library Staff', action: 'UPDATE_CONDITION', details: 'id 8 "Beloved" by Toni Morrison: Available -> Lost', timestamp: stamp(26) },
  { id: 6, librarianId: null, librarianName: 'unknown', action: 'FAILED_LOGIN', details: 'Bad password', timestamp: stamp(30) },
  { id: 7, librarianId: 1, librarianName: 'System Administrator', action: 'DEACTIVATE_MEMBER', details: 'id 5 Priya Raman', timestamp: stamp(48) },
  { id: 8, librarianId: 2, librarianName: 'Library Staff', action: 'ADD_MEMBER', details: 'Noah Bennett', timestamp: stamp(72) }
];

let nextBook = 9, nextMember = 7, nextIssue = 6, nextLog = 9;

const CREDENTIALS = {
  admin: { password: 'admin123', librarian: { id: 1, username: 'admin', fullName: 'System Administrator', role: 'ADMIN' } },
  librarian: { password: 'librarian123', librarian: { id: 2, username: 'librarian', fullName: 'Library Staff', role: 'LIBRARIAN' } }
};
const sessions = new Map(); // token -> librarian

/* ------------------------------------------------------------------ helpers */
const todayISO = new Date().toISOString().slice(0, 10);
function daysLate(due) {
  if (!due) return 0;
  const d = Math.floor((Date.parse(todayISO) - Date.parse(due)) / 86400000);
  return d > 0 ? d : 0;
}
function log(librarianId, action, details) {
  const found = librarianId ? Object.values(CREDENTIALS).map(c => c.librarian).find(l => l.id === librarianId) : null;
  logs.unshift({
    id: nextLog++, librarianId: librarianId || null,
    librarianName: found ? found.fullName : 'unknown',
    action, details, timestamp: stamp(0)
  });
}
function sessionOf(req) {
  const h = req.headers['authorization'] || '';
  const token = h.startsWith('Bearer ') ? h.slice(7) : null;
  return token ? sessions.get(token) || null : null;
}
function send(res, status, body, type) {
  const payload = type === 'raw' ? body : JSON.stringify(body);
  const buf = Buffer.isBuffer(body) ? body : Buffer.from(payload, 'utf8');
  res.writeHead(status, { 'Content-Type': type || 'application/json; charset=UTF-8' });
  res.end(buf);
}
const err = (res, status, message) => send(res, status, { error: message });
function readBody(req) {
  return new Promise((resolve, reject) => {
    let data = '';
    req.on('data', c => { data += c; if (data.length > 2e6) req.destroy(); });
    req.on('end', () => resolve(data));
    req.on('error', reject);
  });
}
const isInt = (s) => /^\d+$/.test(s);
function validationError(b) {
  if (!b) return 'Bad request';
  if (!b.title || !String(b.title).trim()) return 'Title is required';
  if (String(b.title).length > 200) return 'Title must be 200 characters or fewer';
  if (!b.author || !String(b.author).trim()) return 'Author is required';
  if (String(b.author).length > 100) return 'Author must be 100 characters or fewer';
  if (b.isbn) {
    const d = String(b.isbn).trim().replace(/-/g, '');
    if (!/^\d{10}$/.test(d) && !/^\d{13}$/.test(d)) return 'ISBN must be 10 or 13 digits (hyphens allowed)';
    if (books.some(x => x.isbn === String(b.isbn).trim() && String(x.id) !== String(b.id))) return 'A book with this ISBN already exists';
  }
  const year = Number(b.publishYear || 0);
  if (year !== 0 && (year < 1450 || year > new Date().getFullYear() + 1)) return `Publish year must be between 1450 and ${new Date().getFullYear() + 1}`;
  if (b.genre && String(b.genre).length > 50) return 'Genre must be 50 characters or fewer';
  if (Number(b.totalCopies || 1) < 1) return 'Total copies must be at least 1';
  return null;
}
function memberValidationError(m) {
  if (!m) return 'Bad request';
  if (!m.name || !String(m.name).trim()) return 'Name is required';
  if (String(m.name).length > 100) return 'Name must be 100 characters or fewer';
  if (m.email) {
    if (String(m.email).length > 120) return 'Email must be 120 characters or fewer';
    if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(String(m.email))) return 'Email must be a valid address';
  }
  if (m.phone && String(m.phone).length > 30) return 'Phone must be 30 characters or fewer';
  return null;
}
function bookShape(b) { return b; }

/* ------------------------------------------------------------------- router */
const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, 'http://localhost');
  const p = url.pathname;
  const method = req.method;

  try {
    /* ---- static page ---- */
    if (p === '/' || p === '/index.html') {
      const html = fs.readFileSync(INDEX);
      return send(res, 200, html, 'text/html; charset=UTF-8');
    }
    if (!p.startsWith('/api/')) return err(res, 404, 'Not found');

    /* ---- auth ---- */
    if (p === '/api/login') {
      if (method !== 'POST') return err(res, 405, 'Method not allowed');
      const body = JSON.parse((await readBody(req)) || '{}');
      if (!body.username || !body.password) return err(res, 400, 'Username and password are required');
      const found = CREDENTIALS[String(body.username).trim()];
      if (!found || found.password !== body.password) {
        if (found) log(found.librarian.id, 'FAILED_LOGIN', 'Bad password');
        return err(res, 401, 'Invalid username or password');
      }
      const token = crypto.randomUUID();
      sessions.set(token, found.librarian);
      log(found.librarian.id, 'LOGIN', 'Logged in');
      return send(res, 200, { token, librarian: found.librarian });
    }
    if (p === '/api/logout') {
      if (method !== 'POST') return err(res, 405, 'Method not allowed');
      const s = sessionOf(req);
      if (!s) return err(res, 401, 'Not logged in');
      sessions.delete(sessionOfToken(req));
      log(s.id, 'LOGOUT', 'Logged out');
      return send(res, 200, { message: 'Logged out' });
    }

    const session = sessionOf(req);
    if (!session) return err(res, 401, 'Not authenticated');

    /* ---- dashboard ---- */
    if (p === '/api/dashboard') {
      if (method !== 'GET') return err(res, 405, 'Method not allowed');
      return send(res, 200, {
        totalBooks: books.reduce((a, b) => a + b.totalCopies, 0),
        availableNow: books.reduce((a, b) => a + b.availableCopies, 0),
        issuedNow: issues.filter(i => i.status === 'ISSUED').length,
        overdue: issues.filter(i => i.status === 'ISSUED' && daysLate(i.dueDate) > 0).length,
        activeMembers: members.filter(m => m.activeStatus).length
      });
    }

    /* ---- logs ---- */
    if (p === '/api/logs') {
      if (method !== 'GET') return err(res, 405, 'Method not allowed');
      return send(res, 200, logs.slice(0, 200));
    }

    /* ---- books ---- */
    if (p === '/api/books') {
      if (method === 'GET') {
        let list = books.slice();
        const q = (k) => (url.searchParams.get(k) || '').trim().toLowerCase();
        const filters = { title: q('title'), author: q('author'), genre: q('genre'), isbn: q('isbn') };
        if (Object.values(filters).some(Boolean)) {
          list = list.filter(b =>
            (!filters.title || String(b.title).toLowerCase().includes(filters.title)) &&
            (!filters.author || String(b.author).toLowerCase().includes(filters.author)) &&
            (!filters.genre || String(b.genre || '').toLowerCase().includes(filters.genre)) &&
            (!filters.isbn || String(b.isbn || '').toLowerCase().includes(filters.isbn)));
        }
        return send(res, 200, list);
      }
      if (method === 'POST') {
        const body = JSON.parse(await readBody(req));
        const problem = validationError(body);
        if (problem) return err(res, 400, problem);
        const total = Math.max(1, Number(body.totalCopies || 1));
        const created = {
          id: nextBook++, title: String(body.title).trim(), author: String(body.author).trim(),
          isbn: body.isbn ? String(body.isbn).trim() : null, publishYear: Number(body.publishYear || 0),
          genre: body.genre || null, condition: 'Available', totalCopies: total, availableCopies: total
        };
        books.push(created);
        log(session.id, 'ADD_BOOK', `"${created.title}" by ${created.author}`);
        return send(res, 201, { message: 'Book added' });
      }
      return err(res, 405, 'Method not allowed');
    }

    if (p === '/api/books/export') {
      if (method !== 'GET') return err(res, 405, 'Method not allowed');
      const rows = [['title', 'author', 'isbn', 'publishYear', 'genre', 'condition', 'totalCopies']];
      books.forEach(b => rows.push([b.title, b.author, b.isbn || '', b.publishYear || '', b.genre || '', b.condition, b.totalCopies]));
      const csv = rows.map(r => r.map(c => (/[",\n]/.test(String(c)) ? '"' + String(c).replace(/"/g, '""') + '"' : c)).join(',')).join('\n');
      res.writeHead(200, { 'Content-Type': 'text/csv; charset=UTF-8', 'Content-Disposition': 'attachment; filename="books.csv"' });
      return res.end(csv);
    }

    if (p === '/api/books/import') {
      if (method !== 'POST') return err(res, 405, 'Method not allowed');
      const text = await readBody(req);
      let added = 0, skipped = 0, errors = 0;
      text.split(/\r?\n/).forEach((line, idx) => {
        if (!line.trim()) return;
        if (idx === 0 && /title/i.test(line.split(',')[0])) return;
        const [title, author, isbn, year, genre] = line.split(',');
        const row = { title: (title || '').trim(), author: (author || '').trim(), isbn: (isbn || '').trim() || null, publishYear: Number(year || 0), genre: (genre || '').trim() || null, totalCopies: 1 };
        if (validationError(row)) { errors++; return; }
        if (row.isbn && books.some(b => b.isbn === row.isbn)) { skipped++; return; }
        books.push({ id: nextBook++, title: row.title, author: row.author, isbn: row.isbn, publishYear: row.publishYear, genre: row.genre, condition: 'Available', totalCopies: 1, availableCopies: 1 });
        added++;
      });
      log(session.id, 'IMPORT_BOOKS', `added=${added}, skipped=${skipped}, errors=${errors}`);
      return send(res, 200, { added, skipped, errors });
    }

    let m = p.match(/^\/api\/books\/(\d+)(?:\/(condition))?$/);
    if (m) {
      const id = Number(m[1]);
      const idx = books.findIndex(b => b.id === id);
      if (m[2] === 'condition') {
        if (method !== 'PUT') return err(res, 405, 'Method not allowed');
        if (idx < 0) return err(res, 404, 'Book not found');
        const body = JSON.parse(await readBody(req));
        const next = body.condition;
        if (!['Available', 'Damaged', 'Lost'].includes(next)) return err(res, 400, 'Condition must be Available, Damaged, or Lost');
        const b = books[idx];
        const wasGood = b.condition === 'Available', nowGood = next === 'Available';
        if (wasGood && !nowGood) b.availableCopies = Math.max(0, b.availableCopies - 1);
        else if (!wasGood && nowGood) b.availableCopies = Math.min(b.totalCopies, b.availableCopies + 1);
        b.condition = next;
        log(session.id, 'UPDATE_CONDITION', `id ${b.id} "${b.title}": condition -> ${next}`);
        return send(res, 200, { message: 'Condition updated', book: b });
      }
      if (idx < 0) return err(res, 404, 'Book not found');
      if (method === 'GET') return send(res, 200, books[idx]);
      if (method === 'PUT') {
        const body = JSON.parse(await readBody(req));
        const existing = books[idx];
        const problem = validationError({ ...body, id });
        if (problem) return err(res, 400, problem);
        const newTotal = Math.max(1, Number(body.totalCopies || 1));
        const oldTotal = existing.totalCopies;
        let available = newTotal >= oldTotal ? existing.availableCopies + (newTotal - oldTotal) : Math.min(existing.availableCopies, newTotal);
        available = Math.max(0, Math.min(available, newTotal));
        books[idx] = { ...existing, title: String(body.title).trim(), author: String(body.author).trim(), isbn: body.isbn || null, publishYear: Number(body.publishYear || 0), genre: body.genre || null, totalCopies: newTotal, availableCopies: available };
        log(session.id, 'UPDATE_BOOK', `id ${id} -> "${books[idx].title}" by ${books[idx].author}`);
        return send(res, 200, { message: 'Book updated' });
      }
      if (method === 'DELETE') {
        const b = books[idx];
        books.splice(idx, 1);
        issues = issues.filter(i => i.bookId !== id);
        log(session.id, 'DELETE_BOOK', `id ${id} "${b.title}" by ${b.author}`);
        return send(res, 200, { message: 'Book deleted' });
      }
      return err(res, 405, 'Method not allowed');
    }

    /* ---- members ---- */
    if (p === '/api/members') {
      if (method === 'GET') return send(res, 200, members.slice().sort((a, b) => (b.activeStatus - a.activeStatus) || a.name.localeCompare(b.name)));
      if (method === 'POST') {
        const body = JSON.parse(await readBody(req));
        const problem = memberValidationError(body);
        if (problem) return err(res, 400, problem);
        members.push({ id: nextMember++, name: String(body.name).trim(), email: body.email || null, phone: body.phone || null, activeStatus: true });
        log(session.id, 'ADD_MEMBER', body.name);
        return send(res, 201, { message: 'Member added' });
      }
      return err(res, 405, 'Method not allowed');
    }

    m = p.match(/^\/api\/members\/(\d+)$/);
    if (m) {
      const id = Number(m[1]);
      const idx = members.findIndex(x => x.id === id);
      if (idx < 0) return err(res, 404, 'Member not found');
      if (method === 'PUT') {
        const body = JSON.parse(await readBody(req));
        const problem = memberValidationError(body);
        if (problem) return err(res, 400, problem);
        const restored = !members[idx].activeStatus && body.activeStatus === true;
        members[idx] = { id, name: String(body.name).trim(), email: body.email || null, phone: body.phone || null, activeStatus: !!body.activeStatus };
        log(session.id, restored ? 'UPDATE_MEMBER' : 'UPDATE_MEMBER', `id ${id} ${members[idx].name}`);
        return send(res, 200, { message: 'Member updated' });
      }
      if (method === 'DELETE') {
        members[idx].activeStatus = false;
        log(session.id, 'DEACTIVATE_MEMBER', `id ${id} ${members[idx].name}`);
        return send(res, 200, { message: 'Member deactivated' });
      }
      return err(res, 405, 'Method not allowed');
    }

    /* ---- circulation ---- */
    if (p === '/api/issues') {
      if (method === 'GET') return send(res, 200, issues.slice().reverse());
      if (method === 'POST') {
        const body = JSON.parse(await readBody(req));
        if (!(body.bookId > 0) || !(body.memberId > 0)) return err(res, 400, 'bookId and memberId are required');
        const book = books.find(b => b.id === Number(body.bookId));
        const member = members.find(x => x.id === Number(body.memberId));
        if (!book) return err(res, 404, 'Book not found');
        if (!member) return err(res, 404, 'Member not found');
        if (book.availableCopies < 1) return err(res, 409, 'No copies available');
        if (!member.activeStatus) return err(res, 409, 'Member is not active');
        if (issues.some(i => i.bookId === book.id && i.memberId === member.id && i.status === 'ISSUED')) return err(res, 409, 'This member already has this book on loan');
        book.availableCopies -= 1;
        const issueDate = todayISO;
        const dueDate = daysAhead(14);
        const rec = { id: nextIssue++, bookId: book.id, memberId: member.id, bookTitle: book.title, memberName: member.name, issueDate, dueDate, returnDate: null, status: 'ISSUED', fineAmount: 0 };
        issues.push(rec);
        log(session.id, 'ISSUE_BOOK', `Book id ${book.id} (${book.title}) to ${member.name}, due ${dueDate}`);
        return send(res, 201, { message: 'Book issued', record: rec });
      }
      return err(res, 405, 'Method not allowed');
    }

    m = p.match(/^\/api\/issues\/(\d+)\/return$/);
    if (m) {
      if (method !== 'POST') return err(res, 405, 'Method not allowed');
      const id = Number(m[1]);
      const rec = issues.find(i => i.id === id);
      if (!rec) return err(res, 404, 'Loan not found');
      if (rec.status === 'RETURNED') return err(res, 409, 'Loan already returned');
      const late = daysLate(rec.dueDate);
      rec.returnDate = todayISO;
      rec.status = 'RETURNED';
      rec.fineAmount = Number((late * 0.5).toFixed(2));
      const book = books.find(b => b.id === rec.bookId);
      if (book) book.availableCopies = Math.min(book.totalCopies, book.availableCopies + 1);
      log(session.id, 'RETURN_BOOK', `Record id ${rec.id} (${rec.bookTitle})${rec.fineAmount > 0 ? ', fine $' + rec.fineAmount : ''}`);
      return send(res, 200, { message: 'Book returned', record: rec });
    }

    return err(res, 404, 'Not found');
  } catch (e) {
    console.error('[mock]', method, p, e.message);
    return err(res, 500, e.message || 'Server error');
  }
});

function sessionOfToken(req) {
  const h = req.headers['authorization'] || '';
  return h.startsWith('Bearer ') ? h.slice(7) : null;
}

server.listen(PORT, () => {
  console.log('Mock library API on http://localhost:' + PORT);
  console.log('  login: admin / admin123   (or librarian / librarian123)');
});
