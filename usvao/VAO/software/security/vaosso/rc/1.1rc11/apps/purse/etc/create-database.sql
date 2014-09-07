-- -----------
-- 
-- initialization of the database
-- 
-- -----------

-- create database if not exists purseDatabase;
-- use purseDatabase;

-- Schema for Purse User Management Database

-- Status Table
create table status_table
(
	status_id int auto_increment,
	status_name varchar(100) not null,
	status_description text not null,
	constraint status_uk_cons unique(status_name),
	constraint status_pk_cons primary key(status_id)
);

-- Role Table
create table role_table
(
	role_id int auto_increment,
	role_name varchar(100) not null,
	role_description text not null,
	constraint role_uk_cons unique(role_name),
	constraint role_pk_cons primary key(role_id)
);

CREATE TABLE preference_type (
  id bigint(20) NOT NULL auto_increment,
  name varchar(255) default NULL,
  description varchar(255) default NULL,
  PRIMARY KEY  (id)
);

CREATE TABLE ra_table (
  ra_id int(11) NOT NULL auto_increment,
  ra_name varchar(100) NOT NULL,
  ra_email varchar(100) NOT NULL,
  ra_description text NOT NULL,
  PRIMARY KEY  (ra_id),
  constraint ra_uk_cons_name UNIQUE KEY (ra_name),
  constraint ra_uk_cons_email UNIQUE KEY (ra_email)
);

-- User Table
create table user_table (
    user_id int(11) not null auto_increment,
    token varchar(200) not null,
    first_name text not null,
    last_name text not null,
    contact_person text not null,
    user_name varchar(200) not null,
    institution text not null,
    project_name text not null,
    email text not null,
    phone text not null,
    country text,
    dn text,
    status_id int(11) not null,
    creation_time datetime default NULL,
    created_from_addr text default '',
    number_logins int(11) default NULL,
    last_access_time date default NULL,
    ra_id int(11) default NULL,
    password_sha blob not null,
    salt blob not null,
    password_method varchar(16) not null,
    portal_confirm_url text,
    portal_name text,
    is_admin tinyint(1) default NULL,
    constraint status_cons foreign key(status_id) references status_table(status_id),
    constraint username_uk_cons unique(user_name),
    constraint token_uk_cons unique(token),
    constraint user_pk_cons primary key(user_id),
    constraint ra_cons foreign key (ra_id) references ra_table(ra_id)
);

-- Salted hash view for pam_mysql
create view user_sha as
      select user_name, CONCAT(password_sha, '|', salt) as password_sha, status_id
      from user_table;


-- User groups
create table group_table
(
	group_id int AUTO_INCREMENT,
	group_name varchar(200) not null,
	group_desc text not null,
	constraint group_uk_cons unique(group_name),
	constraint gp_pk primary key(group_id)
);

-- User/Group
create table user_group_table
(
	group_id int not null,
	user_id int not null,
	constraint foreign1 foreign key(group_id) references group_table(group_id),
	constraint foreign2 foreign key(user_id) references user_table(user_id),
	constraint user_gp_pk primary key(group_id, user_id)
);

-- User, roles association
create table user_roles_table
(
	user_id int not null,
	role_id int not null,
	constraint user_cons foreign key(user_id) references user_table(user_id),
	constraint role_cons foreign key(role_id) references role_table(role_id),
	constraint pk_cons primary key(role_id, user_id)
);

-- other tables

create table openid_association (
  handle varchar(64) not null,
  type text,
  mackey text,
  expdate datetime default null,
  primary key  (handle)
);

create table portal (
  id bigint(20) not null auto_increment,
  name varchar(255) unique not NULL,
  description varchar(1023) default NULL,
  url varchar(511) default NULL,
  curator_id bigint(20) default NULL,
  status int default 0,
  PRIMARY KEY  (id),
  KEY (curator_id)
);

create table portal_urls (
  id bigint(20) not null auto_increment,
  portal_id bigint(20) not null,
  hostname varchar(255) not null,
  path varchar(1023) default "/",
  PRIMARY KEY (id),
  KEY (portal_id)
);

CREATE TABLE replication_monitor (
  id bigint(20) unsigned NOT NULL auto_increment,
  version int(11) default NULL,
  server varchar(100) default NULL,
  last_check timestamp NOT NULL default CURRENT_TIMESTAMP on update CURRENT_TIMESTAMP,
  UNIQUE KEY id (id)
);

CREATE TABLE user_preference (
  id bigint(20) NOT NULL auto_increment,
  value varchar(255) default NULL,
  portal_id bigint(20) default NULL,
  user_table_id bigint(20) default NULL,
  preference_type_id bigint(20) NOT NULL,
  PRIMARY KEY  (id),
  KEY (preference_type_id),
  KEY (user_table_id),
  KEY (portal_id)
);


CREATE TABLE user_session (
  id bigint(20) NOT NULL auto_increment,
  token varchar(255) default NULL,
  create_time datetime default NULL,
  expire_time datetime default NULL,
  user_table_id bigint(20) default NULL,
  host_address varchar(255) default NULL,
  PRIMARY KEY  (id),
  KEY (user_table_id)
);

