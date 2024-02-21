grammar Javamm;

@header {
    package pt.up.fe.comp2024;
}

SINGLE_LINE_COMMENT : '//' .*? ('\n'|EOF) -> skip;
MULTI_LINE_COMMENT : '/*' .*? '*/' -> skip;
WS : [ \t\n\r\f]+ -> skip ;

LETTER : [a-zA-Z];
DIGIT : [0-9];

INTEGER : ('0'|[1-9]) DIGIT*; // 0 or any non-zero digit followed by any number of digits so we have numbers in base 10
ID : (LETTER | '$' | '_') (LETTER|DIGIT| '_' | '$')*;

EQUALS : '=';
IMPORT : 'import';
EXTENDS : 'extends';
SEMI : ';';
DOT : '.';

LCURLY : '{' ;
RCURLY : '}' ;
LPAREN : '(' ;
RPAREN : ')' ;
MUL : '*' ;
ADD : '+' ;

CLASS : 'class' ;
INT : 'int' ;
PUBLIC : 'public' ;
RETURN : 'return' ;


program
    : (importDecl)* classDecl EOF
    ;

importDecl
    : IMPORT name+=ID (DOT name+=ID)* SEMI #ImportStatement
    ;

classDecl
    : CLASS name=ID (EXTENDS parent=ID)? LCURLY
    (varDecl)* (methodDecl)* RCURLY
    ;

varDecl
    : type name=ID SEMI
    ;

type
    : name= INT ;

methodDecl locals[boolean isPublic=false]
    : (PUBLIC {$isPublic=true;})?
        type name=ID
        LPAREN param RPAREN
        LCURLY varDecl* stmt* RCURLY
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



