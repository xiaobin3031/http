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