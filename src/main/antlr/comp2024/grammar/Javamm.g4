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
COMMA : ',';
LCURLY : '{' ;
RCURLY : '}' ;
LPAREN : '(' ;
RPAREN : ')' ;
LBRACKET : '[' ;
RBRACKET : ']' ;

// Operators
MUL : '*' ;
DIV : '/' ;
ADD : '+' ;
SUB : '-' ;
AND : '&&' ;
LT : '<' ;
NOT : '!' ;

// Keywords
IMPORT : 'import';
EXTENDS : 'extends';
STATIC : 'static';
MAIN : 'main';
CLASS : 'class';
PUBLIC : 'public';
RETURN : 'return';
IF : 'if';
ELSE : 'else';
WHILE : 'while';
LENGTH : 'length';
THIS : 'this';
NEW : 'new';
TRUE : 'true';
FALSE : 'false';

// Types
INT : 'int' ;
STRING : 'String';
BOOLEAN : 'boolean';
ARRAYTYPESUFFIX : LBRACKET RBRACKET;
VARARGSUFFIX : '...';
VOID : 'void' ;

// Rules
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
    : LCURLY (stmt)* RCURLY #ScopeStmt
    | IF LPAREN expr RPAREN stmt ELSE stmt #IfStmt
    | WHILE LPAREN expr RPAREN stmt #WhileStmt
    | expr SEMI #ExprStmt
    | name=ID EQUALS expr SEMI #AssignStmt
    | name=ID LBRACKET expr RBRACKET EQUALS expr SEMI #ArrayAssignStmt
    ;

expr
    : LPAREN expr RPAREN #ParenExpr
    | expr LBRACKET expr RBRACKET #ArrayAccessOp
    | NEW INT LBRACKET expr RBRACKET #ArrayDeclaration
    | NEW name=ID LPAREN RPAREN #ObjectDeclaration
    | expr DOT LENGTH #Length
    | expr DOT name=ID LPAREN (expr (COMMA expr)*)? RPAREN #MethodCall
    | LBRACKET (expr (COMMA expr)*)? RBRACKET #ArrayExpression
    | NOT expr #UnaryOp
    | expr op=(MUL | DIV) expr #BinaryOp
    | expr op=(ADD | SUB) expr #BinaryOp
    | expr op=LT expr #BinaryOp
    | expr op=AND expr #BinaryOp
    | value=ID #Identifier
    | value=INTEGER #Integer
    | value=TRUE #Boolean
    | value=FALSE #Boolean
    | value=THIS #This
    ;