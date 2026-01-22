CREATE TABLE visitors (
	id uuid DEFAULT gen_random_uuid() NOT NULL,
	ip varchar(64) NOT NULL,
	details varchar(256) NULL,
	visit_time timestamp NOT NULL,
	CONSTRAINT visitors_pkey PRIMARY KEY (id)
);

--#######################################################################


-- select count(*) from visitors where visit_time >= '20260111 00:00:00.000'