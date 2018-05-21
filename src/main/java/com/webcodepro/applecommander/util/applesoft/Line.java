package com.webcodepro.applecommander.util.applesoft;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** An AppleSoft BASIC Line representation. */
public class Line {
	public final Program program;
	public final int lineNumber;
	public final List<Statement> statements = new ArrayList<>();
	
	public Line(int lineNumber, Program program) {
		Objects.requireNonNull(program);
		this.lineNumber = lineNumber;
		this.program = program;
	}
	
	public Optional<Line> nextLine() {
		int i = program.lines.indexOf(this);
		if (i == -1 || i+1 >= program.lines.size()) {
			return Optional.empty();
		}
		return Optional.of(program.lines.get(i+1));
	}
	
	public Line accept(Visitor t) {
		return t.visit(this);
	}
}
