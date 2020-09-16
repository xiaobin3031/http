create table http_host_detail_new
(
	id int auto_increment,
	parent_id int not null,
	url varchar(1024) unique not null,
	url_state int default 200 not null,
	state int default 0 not null,
	name_c varchar(2048) null,
	name_e varchar(2048) null,
	level int default 0 not null,
	create_time bigint not null,
	constraint http_host_detail_new_pk
		primary key (id)
);

create index http_host_detail_new_create_time_index
	on http_host_detail_new (create_time);

create table sequence(
    name varchar(50) not null primary key,
    current_value BIGINT not null DEFAULT 0,
    increment int not null DEFAULT 1,
    max_value BIGINT,  -- 最大值
    initial_value BIGINT -- 初始值，当当前值大于最大值，回到初始值
);

insert into sequence(name, current_value, increment, max_value, initial_value)
values('http_host_detail', 1, 1, 4294967295, 1);

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


create table uuid_id
(
    id   int auto_increment
        primary key,
    uuid varchar(32) not null
);

create index uuid_id_uuid_index
    on uuid_id (uuid);

insert into uuid_id(uuid) select distinct id from http_host_detail;

insert into http_host_detail_new(id, parent_id, url, url_state, name_c, name_e, level, create_time)
select (select id from uuid_id where uuid = h.id),
       (select id from uuid_id where uuid = h.parent_id),
       url, url_state, name_c, name_e, level, create_time
       from
http_host_detail h
where exists(select 1 from uuid_id where uuid = h.id)
and exists(select 1 from uuid_id where uuid = h.parent_id);

alter table http_host_detail rename to http_host_detail_old;
alter table http_host_detail_new rename to http_host_detail;

update sequence set current_value = (select max(id) from http_host_detail) where name = 'http_host_detail';

truncate table uuid_id;

create table http_host_new
(
	id int auto_increment,
	host varchar(64) not null,
	name_c varchar(256) null,
	protocol varchar(16) not null,
	port int not null,
	ip varchar(128) null,
	url_state int default 200 not null,
	state int default 0 not null,
	create_time bigint not null,
	update_time bigint null,
	constraint http_host_new_pk
		primary key (id)
);

insert into http_host_new(host, name_c, protocol, port, ip, url_state, state, create_time, update_time)
select host, name_c, protocol, port, ip, url_state, state, create_time, update_time
from http_host;

alter table http_host rename to http_host_old;
alter table http_host_new rename to http_host;