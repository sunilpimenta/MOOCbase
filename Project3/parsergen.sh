#! /usr/bin/bash
rm -rf src/main/java/edu/berkeley/cs186/database/cli/parser
jjtree MoocParser.jjt
javacc src/main/java/edu/berkeley/cs186/database/cli/parser/MoocParser.jj
rm src/main/java/edu/berkeley/cs186/database/cli/parser/MoocParser.jj
