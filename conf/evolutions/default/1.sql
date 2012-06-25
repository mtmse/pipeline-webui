# --- Created by Ebean DDL
# To stop Ebean DDL generation, remove this comment and start using Evolutions

# --- !Ups

create table job (
  id                        varchar(255) not null,
  nicename                  varchar(255),
  created                   datetime,
  started                   datetime,
  finished                  datetime,
  user                      bigint,
  notified_created          tinyint(1) default 0,
  notified_complete         tinyint(1) default 0,
  constraint pk_job primary key (id))
;

create table setting (
  name                      varchar(255) not null,
  value                     varchar(255),
  constraint pk_setting primary key (name))
;

create table upload (
  id                        bigint auto_increment not null,
  absolute_path             varchar(255),
  content_type              varchar(255),
  uploaded                  datetime,
  user                      bigint,
  job                       varchar(255),
  constraint pk_upload primary key (id))
;

create table user (
  id                        bigint auto_increment not null,
  email                     varchar(255),
  name                      varchar(255),
  password                  varchar(255),
  admin                     tinyint(1) default 0,
  active                    tinyint(1) default 0,
  password_link_sent        datetime,
  constraint pk_user primary key (id))
;




# --- !Downs

SET FOREIGN_KEY_CHECKS=0;

drop table job;

drop table setting;

drop table upload;

drop table user;

SET FOREIGN_KEY_CHECKS=1;

