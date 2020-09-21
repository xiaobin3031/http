create table sequence(
    name varchar(50) not null primary key,
    current_value BIGINT not null DEFAULT 0,
    increment int not null DEFAULT 1,
    max_value BIGINT,  -- 最大值
    initial_value BIGINT -- 初始值，当当前值大于最大值，回到初始值
);

insert into sequence(name, current_value, increment, max_value, initial_value)
values('network_uri', 1, 1, 4294967295, 1);

-- 这一句需要root或者dba权限
set global log_bin_trust_function_creators=TRUE;

delimiter ;;
create function nextval(seq_name varchar(50))
returns bigint
contains sql
begin
    update sequence set current_value = current_value + increment where name = seq_name;
    return curval(seq_name);
end ;;

delimiter ;;
create function curval(seq_name varchar(50))
returns bigint
contains sql
begin
    SELECT current_value,max_value,initial_value INTO @current_value, @max_value, @initial_value
    FROM sequence
    WHERE name = seq_name;
    if(@current_value>@max_value) then
        UPDATE sequence
        SET current_value = initial_value
        WHERE name = seq_name;
        set @current_value=@initial_value;
    end if;
    RETURN @current_value;
end ;;

delimiter ;


-- auto-generated definition
create table network_uri
(
    id            int           not null
        primary key,
    protocol      varchar(16)   not null comment '协议',
    uri           varchar(256)  not null comment '地址',
    server        varchar(256)  null comment '服务器类型',
    contentType   varchar(64)   null,
    contentLength int default 0 not null,
    status        int default 0 not null comment '状态',
    message       varchar(512)  null comment '信息',
    level         int default 0 not null comment '等级',
    constraint network_uri_uri_uindex
        unique (uri)
)
    comment 'uri';

create unique index network_uri_uri_uindex
	on network_uri (uri);

	create table constant
(
    id    int auto_increment
        primary key,
    type  varchar(128)  not null comment '类型',
    value varchar(1024) not null
)
    comment '常量表';