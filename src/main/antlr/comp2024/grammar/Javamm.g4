grammar Javamm;

@header {
    package pt.up.fe.comp2024;
}

// Comments
SINGLE_LINE_COMMENT : '//' .*? ('\n'|EOF) -> skip;
MULTI_LINE_COMMENT : '/*' .*? '*/' -> skip;
WS : [ \t\n\r\f]+ -> skip ;

// Basic Tokens
LETTER : [a-zA-Z];
DIGIT : [0-9];

INTEGER : ('0'|[1-9]) DIGIT*; // 0 or any non-zero digit followed by any number of digits so we have numbers in base 10
ID : (LETTER | '$' | '_') (LETTER|DIGIT| '_' | '$')*;

// Symbols
EQUALS : '=';
SEMI : ';';
DOT : '.';
LCURLY : '{' ;
RCURLY : '}' ;
LPAREN : '(' ;
RPAREN : ')' ;
LBRACKET : '[' ;
RBRACKET : ']' ;
MUL : '*' ;
ADD : '+' ;

// Keywords
IMPORT : 'import';
EXTENDS : 'extends';
STATIC : 'static';
MAIN : 'main';
CLASS : 'class' ;
PUBLIC : 'public' ;
RETURN : 'return' ;

// Types
INT : 'int' ;
STRING : 'String';
BOOLEAN : 'boolean';
ARRAYTYPESUFFIX : LBRACKET RBRACKET;
VARARGSUFFIX : '...';
VOID : 'void' ;


program
    : (importDecl)* classDecl EOF
    ;

importDecl
    : IMPORT name+=ID (DOT name+=ID)* SEMI #ImportDeclaration
    ;

classDecl
    : CLASS name=ID (EXTENDS parent=ID)? LCURLY
    (varDecl)* (methodDecl)* RCURLY #ClassDeclaration
    ;

varDecl
    : type name=ID SEMI #VarDeclaration
    ;

methodDecl
    : (PUBLIC)? type name=ID LPAREN (param (COMMA param)*)? RPAREN LCURLY
    (varDecl)* (stmt)* RETURN expr SEMI RCURLY #MethodDeclaration
    | (PUBLIC)? STATIC VOID name=MAIN LPAREN STRING ARRAYTYPESUFFIX paramName=ID RPAREN LCURLY
    (varDecl)* (stmt)* RCURLY #MainMethodDeclaration
    ;

type
    : type ARRAYTYPESUFFIX #ArrayType
    | type VARARGSUFFIX #VarArgType
    | name=INT #IntType
    | name=STRING #StringType
    | name=BOOLEAN #BooleanType
    | name=ID #NamedType
    ;



param
    : type name=ID
    ;

stmt
    : expr EQUALS expr SEMI #AssignStmt //
    | RETURN expr SEMI #ReturnStmt
    ;

expr
    : expr op= MUL expr #BinaryExpr //
    | expr op= ADD expr #BinaryExpr //
    | value=INTEGER #IntegerLiteral //
    | name=ID #VarRefExpr //
    ;



