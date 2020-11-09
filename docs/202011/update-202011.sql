create index constant_type_index on constant (type);

INSERT INTO http.constant (type, value) VALUES ('http.collect.protocol.ignore', 'ht');