An alternate syntax for Eider Wire that is closer to protobuf. 
This is a work in progress.

```
syntax = "eiderwire";
package io.eider;

enum Corpus {
    UNIVERSAL = 0;
    WEB = 1;
    IMAGES = 2;
    LOCAL = 3;
    NEWS = 4;
    PRODUCTS = 5;
    VIDEO = 6;
}

message SearchRequest {
    string query = 1;
    @repeated
    int32 repeatedField = 2;
    int32 thingMaBob = 3;
    Corpus corpus = 4;
}

message Foobar {
    int64 query = 1;
    int32 page_number = 2;
    int32 result_per_page = 3;
    Corpus corpus = 4;
}
```
