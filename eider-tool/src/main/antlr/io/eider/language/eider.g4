grammar eider;

@header {
    package io.eider.language;
}

eider
  : syntax
    (
        importStatement
      | packageStatement
      | topLevelDef
      | emptyStatement_
    )*
  ;

// Syntax
syntax
  : SYNTAX EQ (EIDER_LIT_SINGLE | EIDER_LIT_DOUBLE) SEMI
  ;

// Import Statement
importStatement
  : IMPORT strLit SEMI
  ;

// Package
packageStatement
  : PACKAGE fullIdent SEMI
  ;

// annotation
annotationStatement
  : AT annotationName
  ;

annotationName
  : fullIdent (LP annotationOptions RP)?
  ;

annotationOptions
  : annotationOption ( COMMA  annotationOption )*
  ;

annotationOption
  : annotationName (EQ constant)?
  ;

// Normal Field
field
  : annotationStatement* type_ fieldName EQ fieldNumber SEMI
  ;

fieldNumber
  : intLit
  ;


// field types
type_
  : DOUBLE
  | FLOAT
  | INT32
  | INT64
  | BOOL
  | FIXSTRING
  | VARSTRING
  | BYTES
  | recordType
  | enumType
  ;

// Top Level definitions
topLevelDef
  : messageDef
  | recordDef
  | enumDef
  ;

// enum
enumDef
  : annotationStatement? ENUM enumName enumBody
  ;

enumBody
  : LC enumElement* RC
  ;

enumElement
  : enumField
  | emptyStatement_
  ;

enumField
  : ident EQ ( MINUS )? intLit SEMI
  ;


// message
messageDef
  : annotationStatement* MESSAGE messageName messageBody
  ;

messageBody
  : LC messageElement* RC
  ;

messageElement
  : field
  | enumDef
  | messageDef
  | recordDef
  | emptyStatement_
  ;

// record
recordDef
  : annotationStatement* RECORD recordName recordBody
  ;

recordBody
  : LC recordElement* RC
  ;

recordElement
  : field
  | enumDef
  | recordDef
  | emptyStatement_
  ;


// lexical

constant
  : fullIdent
  | (MINUS | PLUS )? intLit
  | ( MINUS | PLUS )? floatLit
  | strLit
  | boolLit
  | blockLit
  ;

// not specified in specification but used in tests
blockLit
  : LC ( ident COLON constant )* RC
  ;

emptyStatement_: SEMI;

// Lexical elements
ident: IDENTIFIER | keywords;
fullIdent: ident ( DOT ident )*;
messageName: ident;
recordName: ident;
enumName: ident;
fieldName: ident;
oneofName: ident;
mapName: ident;
messageType: ( DOT )? ( ident DOT )* messageName;
recordType: ( DOT )? ( ident DOT )* recordName;
enumType: ( DOT )? ( ident DOT )* enumName;

intLit: INT_LIT;
strLit: STR_LIT | EIDER_LIT_SINGLE | EIDER_LIT_DOUBLE;
boolLit: BOOL_LIT;
floatLit: FLOAT_LIT;

// keywords
SYNTAX: 'syntax';
IMPORT: 'import';
PUBLIC: 'public';
PACKAGE: 'package';
INT32: 'int32';
INT64: 'int64';
BOOL: 'bool';
FIXSTRING: 'string';
VARSTRING: 'varstring';
DOUBLE: 'double';
FLOAT: 'float';
BYTES: 'bytes';
MAX: 'max';
ENUM: 'enum';
MESSAGE: 'message';
RECORD: 'record';
TARGETSIZE: 'targetsize';
RETURNS: 'returns';

EIDER_LIT_SINGLE: '"eiderwire"';
EIDER_LIT_DOUBLE: '\'eiderwire\'';

// symbols

SEMI: ';';
EQ: '=';
AT: '@';
LP: '(';
RP: ')';
LB: '[';
RB: ']';
LC: '{';
RC: '}';
LT: '<';
GT: '>';
DOT: '.';
COMMA: ',';
COLON: ':';
PLUS: '+';
MINUS: '-';

STR_LIT: ( '\'' ( CHAR_VALUE )*? '\'' ) |  ( '"' ( CHAR_VALUE )*? '"' );
fragment CHAR_VALUE: HEX_ESCAPE | OCT_ESCAPE | CHAR_ESCAPE | ~[\u0000\n\\];
fragment HEX_ESCAPE: '\\' ( 'x' | 'X' ) HEX_DIGIT HEX_DIGIT;
fragment OCT_ESCAPE: '\\' OCTAL_DIGIT OCTAL_DIGIT OCTAL_DIGIT;
fragment CHAR_ESCAPE: '\\' ( 'a' | 'b' | 'f' | 'n' | 'r' | 't' | 'v' | '\\' | '\'' | '"' );

BOOL_LIT: 'true' | 'false';

FLOAT_LIT : ( DECIMALS DOT DECIMALS? EXPONENT? | DECIMALS EXPONENT | DOT DECIMALS EXPONENT? ) | 'inf' | 'nan';
fragment EXPONENT  : ( 'e' | 'E' ) (PLUS | MINUS)? DECIMALS;
fragment DECIMALS  : DECIMAL_DIGIT+;

INT_LIT     : DECIMAL_LIT | OCTAL_LIT | HEX_LIT;
fragment DECIMAL_LIT : ( [1-9] ) DECIMAL_DIGIT*;
fragment OCTAL_LIT   : '0' OCTAL_DIGIT*;
fragment HEX_LIT     : '0' ( 'x' | 'X' ) HEX_DIGIT+ ;

IDENTIFIER: LETTER ( LETTER | DECIMAL_DIGIT )*;

fragment LETTER: [A-Za-z_];
fragment DECIMAL_DIGIT: [0-9];
fragment OCTAL_DIGIT: [0-7];
fragment HEX_DIGIT: [0-9A-Fa-f];

// comments
WS  :   [ \t\r\n\u000C]+ -> skip;
LINE_COMMENT: '//' ~[\r\n]* -> skip;
COMMENT: '/*' .*? '*/' -> skip;

keywords
  : SYNTAX
  | IMPORT
  | PACKAGE
  | INT32
  | INT64
  | BOOL
  | VARSTRING
  | FIXSTRING
  | DOUBLE
  | FLOAT
  | BYTES
  | MAX
  | TARGETSIZE
  | ENUM
  | MESSAGE
  | RECORD
  | RETURNS
  | BOOL_LIT
  ;
