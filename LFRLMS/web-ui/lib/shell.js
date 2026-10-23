// Parses one mongosh-style command, such as
//   db.vehicle.find({ status: "Available" }, { model: 1 }).sort({ model: 1 }).limit(5)
// into plain data. Nothing is evaluated as JavaScript: the arguments are read as relaxed JSON
// (unquoted keys, single quotes, trailing commas, // comments and /regex/flags are allowed).
const { AppError } = require('./db');

class Parser {
  constructor(text) {
    this.text = text;
    this.pos = 0;
  }

  fail(message) {
    throw new AppError(`${message} (at character ${this.pos + 1}).`);
  }

  skip() {
    for (;;) {
      while (/\s/.test(this.text[this.pos] || '')) this.pos += 1;
      if (this.text.startsWith('//', this.pos)) {
        while (this.pos < this.text.length && this.text[this.pos] !== '\n') this.pos += 1;
      } else if (this.text.startsWith('/*', this.pos)) {
        const end = this.text.indexOf('*/', this.pos + 2);
        this.pos = end === -1 ? this.text.length : end + 2;
      } else {
        return;
      }
    }
  }

  peek() {
    this.skip();
    return this.text[this.pos];
  }

  expect(char) {
    if (this.peek() !== char) this.fail(`Expected "${char}"`);
    this.pos += 1;
  }

  identifier() {
    this.skip();
    const match = /^[A-Za-z_$][\w$]*/.exec(this.text.slice(this.pos));
    if (!match) this.fail('Expected a name');
    this.pos += match[0].length;
    return match[0];
  }

  string() {
    const quote = this.text[this.pos];
    let result = '';
    this.pos += 1;
    while (this.pos < this.text.length && this.text[this.pos] !== quote) {
      let char = this.text[this.pos];
      if (char === '\\') {
        this.pos += 1;
        const escaped = this.text[this.pos];
        if (escaped === 'u') {
          char = String.fromCharCode(parseInt(this.text.slice(this.pos + 1, this.pos + 5), 16));
          this.pos += 4;
        } else {
          char = { n: '\n', t: '\t', r: '\r', b: '\b', f: '\f' }[escaped] ?? escaped;
        }
      }
      result += char;
      this.pos += 1;
    }
    if (this.text[this.pos] !== quote) this.fail('Unterminated string');
    this.pos += 1;
    return result;
  }

  regex() {
    const match = /^\/((?:[^/\\\n]|\\.)+)\/([gimsux]*)/.exec(this.text.slice(this.pos));
    if (!match) this.fail('Invalid regular expression');
    this.pos += match[0].length;
    try {
      return new RegExp(match[1], match[2].replace('g', ''));
    } catch (error) {
      return this.fail(`Invalid regular expression: ${error.message}`);
    }
  }

  value() {
    const char = this.peek();
    if (char === '{') return this.object();
    if (char === '[') return this.array();
    if (char === '"' || char === "'") return this.string();
    if (char === '/') return this.regex();
    const number = /^-?(\d+\.?\d*|\.\d+)([eE][+-]?\d+)?/.exec(this.text.slice(this.pos));
    if (number) {
      this.pos += number[0].length;
      return Number(number[0]);
    }
    const word = this.identifier();
    if (word === 'true') return true;
    if (word === 'false') return false;
    if (word === 'null') return null;
    return this.fail(`Unexpected "${word}"; only JSON-style values are accepted`);
  }

  object() {
    const result = {};
    this.expect('{');
    while (this.peek() !== '}') {
      const char = this.peek();
      const key = char === '"' || char === "'" ? this.string() : this.identifier();
      this.expect(':');
      result[key] = this.value();
      if (this.peek() === ',') this.pos += 1;
      else if (this.peek() !== '}') this.fail('Expected "," or "}"');
    }
    this.pos += 1;
    return result;
  }

  array() {
    const result = [];
    this.expect('[');
    while (this.peek() !== ']') {
      result.push(this.value());
      if (this.peek() === ',') this.pos += 1;
      else if (this.peek() !== ']') this.fail('Expected "," or "]"');
    }
    this.pos += 1;
    return result;
  }

  call() {
    const name = this.identifier();
    const args = [];
    this.expect('(');
    while (this.peek() !== ')') {
      args.push(this.value());
      if (this.peek() === ',') this.pos += 1;
      else if (this.peek() !== ')') this.fail('Expected "," or ")"');
    }
    this.pos += 1;
    return { name, args };
  }
}

/** Reads `db.<collection>.<method>(...)` with optional `.sort() .skip() .limit() .explain()` calls after it. */
function parseCommand(input) {
  const text = String(input || '').trim();
  if (!text) throw new AppError('Type a command to run.');
  if (text.length > 8000) throw new AppError('That command is too long.');
  const parser = new Parser(text);
  if (parser.identifier() !== 'db') parser.fail('Commands start with db.<collection>');
  parser.expect('.');
  const collection = parser.identifier();
  parser.expect('.');
  const { name: method, args } = parser.call();
  const chain = [];
  while (parser.peek() === '.') {
    parser.pos += 1;
    chain.push(parser.call());
  }
  if (parser.peek() === ';') parser.pos += 1;
  if (parser.peek() !== undefined) parser.fail('Run one command at a time');
  return { collection, method, args, chain };
}

module.exports = { parseCommand };
