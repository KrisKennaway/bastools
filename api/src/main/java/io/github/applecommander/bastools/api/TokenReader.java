package io.github.applecommander.bastools.api;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StreamTokenizer;
import java.util.LinkedList;
import java.util.Optional;
import java.util.Queue;

import io.github.applecommander.bastools.api.model.ApplesoftKeyword;
import io.github.applecommander.bastools.api.model.Token;

/**
 * The TokenReader, given a text file, generates a series of Tokens (in the compiler sense, 
 * not AppleSoft) for the AppleSoft program.
 *  
 * @author rob
 */
public class TokenReader {
	private boolean hasMore = true;
	// Internal flag just in case we consume the EOL (see REM for instance)s
	private boolean needSyntheticEol = false;
	private Reader reader;
	private StreamTokenizer tokenizer;

	/** A handy method to generate a list of Tokens from a file name. */
	public static Queue<Token> tokenize(String filename) throws FileNotFoundException, IOException {
		try (FileReader fileReader = new FileReader(filename)) {
			return tokenize(fileReader);
		}
	}
	/** A handy method to generate a list of Tokens from a file. */
	public static Queue<Token> tokenize(File file) throws FileNotFoundException, IOException {
		try (FileReader fileReader = new FileReader(file)) {
			return tokenize(fileReader);
		}
	}
	/** A handy method to generate a list of Tokens from an InputStream. */
	public static Queue<Token> tokenize(InputStream inputStream) throws IOException {
		try (InputStreamReader streamReader = new InputStreamReader(inputStream)) {
			return tokenize(streamReader);
		}
	}
	private static Queue<Token> tokenize(Reader reader) throws IOException {
		TokenReader tokenReader = new TokenReader(reader);
		LinkedList<Token> tokens = new LinkedList<>();
		while (tokenReader.hasMore()) {
			// Magic number: maximum number of pieces from the StreamTokenizer that may be combined.
			tokenReader.next(2)
					   .ifPresent(tokens::add);
		}
		return tokens;
	}

	public TokenReader(Reader reader) {
		this.reader = reader;
		this.tokenizer = ApplesoftKeyword.tokenizer(reader);
	}
	
	public boolean hasMore() {
		return hasMore;
	}
	
	public Optional<Token> next(int depth) throws IOException {
		// A cheesy attempt to prevent too much looping...
		if (depth > 0) {
			if (this.needSyntheticEol) {
				this.needSyntheticEol = false;
				int line = tokenizer.lineno();
				return Optional.of(Token.eol(line));
			}
			hasMore = tokenizer.nextToken() != StreamTokenizer.TT_EOF;
			if (hasMore) {
				int line = tokenizer.lineno();
				switch (tokenizer.ttype) {
				case StreamTokenizer.TT_EOL:
					return Optional.of(Token.eol(line));
				case StreamTokenizer.TT_NUMBER:
					return Optional.of(Token.number(line, tokenizer.nval));
				case StreamTokenizer.TT_WORD:
					Optional<ApplesoftKeyword> opt = ApplesoftKeyword.find(tokenizer.sval);
					// REM is special
					if (opt.filter(kw -> kw == ApplesoftKeyword.REM).isPresent()) {
						StringBuilder sb = new StringBuilder();
						while (true) {
							// Bypass the Tokenizer and just read to EOL for the comment
							int ch = reader.read();
							if (ch == '\n') {
								// Recover to the newline so that the next token is a EOL
								// This is needed for parsing!
								this.needSyntheticEol = true;
								break;
							}
							sb.append((char)ch);
						}
						return Optional.of(Token.comment(line, sb.toString()));
					}
					// If we found an Applesoft token, handle it
					if (opt.isPresent()) {
						if (opt.get().parts.size() > 1) {
							// Pull next token and see if it is the 2nd part ("PR#" == "PR", "#"; checking for the "#")
							next(depth-1)
								.filter(t -> opt.get().parts.get(1).equals(t.text))
							    .orElseThrow(() -> new IOException("Expecting: " + opt.get().parts));
						}
						ApplesoftKeyword outKeyword = opt.get();
						// Special case - canonicalize '?' alternate form of 'PRINT'
						if (opt.filter(kw -> kw == ApplesoftKeyword.questionmark).isPresent()) {
							outKeyword = ApplesoftKeyword.PRINT;
						}
						return Optional.of(Token.keyword(line, outKeyword));
					}
					// Check if we found a directive
					if (tokenizer.sval.startsWith("$")) {
						return Optional.of(Token.directive(line, tokenizer.sval));
					}
					// Found an identifier (A, A$, A%).  Test if it is an array ('A(', 'A$(', 'A%(').
					String sval = tokenizer.sval;
					tokenizer.nextToken();
					if (tokenizer.ttype == '(') {
						sval += (char)tokenizer.ttype;
					} else {
						tokenizer.pushBack();
					}
					return Optional.of(Token.ident(line, sval));
				case '"':
					return Optional.of(Token.string(line, tokenizer.sval));
				case '(':
				case ')':
				case ',':
				case ':':
				case '$':
				case '#':
				case ';':
				case '&':
				case '=':
				case '<':
				case '>':
				case '*':
				case '+':
				case '-':
				case '/':
				case '^':
					return Optional.of(
							ApplesoftKeyword.find(String.format("%c", tokenizer.ttype))
							   .map(kw -> Token.keyword(line, kw))
							   .orElse(Token.syntax(line, tokenizer.ttype)));
				case '\\':
				    // Special case: introducing a backslash to ignore the IMMEDIATELY following EOL
				    // If this does not occur, we simply fall through and fail.  That is intentional!
				    if (tokenizer.nextToken() == StreamTokenizer.TT_EOL) {
				        // Consume the EOL and continue on our merry way
				        break;
				    }
				default:
					throw new IOException(String.format(
						"Unknown! ttype=%d, nval=%f, sval=%s\n", tokenizer.ttype, tokenizer.nval, tokenizer.sval));
				}
			}
		}
		return Optional.empty();
	}
}
