CREATE DEFINER=`root`@`%` PROCEDURE `p1`()
begin
    declare IIDD1 varchar(255);
    declare REALNAME1 VARCHAR(255);
		declare ADDDATETIME1 VARCHAR(255);
		declare CENTERID VARCHAR(255);
		declare CENTERVAL1 VARCHAR(255);
		declare DEPARTMENT1 VARCHAR(255);
		declare DEPARTMENTVAL1 VARCHAR(255);
		declare EDUCATION1 VARCHAR(255); -- 学历
		declare protectid VARCHAR(255); -- 项目id
		declare protectname VARCHAR(255); -- 项目名称
		declare fraction1  int(64); -- 分数
		declare randIIDD varchar(255);  -- 随机主键
		declare tempdate VARCHAR(255); -- 临时变量
		declare REWARD_TYPE_ID_v VARCHAR(255); -- 奖励类型id变量  
		declare REWARD_TYPE_NAME_v VARCHAR(255); -- 奖励类型名称变量
		
		declare flag int default 0;
    -- 声明游标
    declare mc cursor for select IIDD,REALNAME,DATE_FORMAT(ADDDATETIME, '%Y-%m-%d') ,CENTER,CENTERVAL,DEPARTMENT,DEPARTMENTVAL,EDUCATION from member ;
		declare continue handler for not found set flag = 1;
    -- 打开游标
    open mc;
		select '开始插入';
		xl:loop 
			if flag=1 then -- 当无法fetch会触发handler continue
					leave xl;
			end if;
		
							fetch mc into IIDD1,REALNAME1,ADDDATETIME1,CENTERID,CENTERVAL1,DEPARTMENT1,DEPARTMENTVAL1,EDUCATION1;
							set randIIDD = (select substring(md5(rand()), 1, 32));
			
							-- 设置变量
-- 							select REWARD_PROTECT_ID into protectid from reward_protect where PROTECT_NAME = EDUCATION1;
-- 							select FRACTION into fraction1 from reward_protect where PROTECT_NAME = EDUCATION1;
							set protectname = EDUCATION1;
							if EDUCATION1 = '高中、中专、初中' THEN  
							set protectid='12f9a56f-f440-404b-b8f8-3c690589f583'; set fraction1 = '100';
							ELSEIF  EDUCATION1 = '大专学历' THEN 
							set protectid='9b95093f-c8a4-4461-8be2-80353dad9a1e'; set fraction1 = '200';
							ELSEIF  EDUCATION1 = '本科学历' THEN 
							set protectid='c6b08830-a55a-447d-8dcb-62606bf2eac8'; set fraction1 = '300';
							ELSEIF  EDUCATION1 = '硕士研究生' THEN 
							set protectid='a2950e01-ee98-42fa-81c0-c723030fb358'; set fraction1 = '400';
							ELSEIF  EDUCATION1 = '博士研究生' THEN 
							set protectid='9ee293ec-3810-40e5-9665-a54bdbd6a0c5'; set fraction1 = '500';
							end if;
							
							set REWARD_TYPE_ID_v = '4e2ef732-2a50-4210-b1ae-5d880a1fa5cb';
							set REWARD_TYPE_NAME_v = '学历';
							
				-- 这里学历基础分
				if EDUCATION1 is not null or EDUCATION1 != '' THEN
					insert into record (
						RECORD_ID, -- 惩罚奖励记录id varchar类型
						USER_ID, -- 获奖id IIDD1
						USER_NAME, -- 获奖姓名 REALNAME1
						CORE_ID, -- 所属中心 CENTERID
						CORE_NAME, -- CENTERVAL1
						DEPARTMENT_ID, -- 所属部门 DEPARTMENT1
						DEPARTMENT_NAME, -- DEPARTMENTVAL1
						HAPPEN, -- 0加分1扣分 0
						REWARD_TYPE_ID, -- 奖惩类型ID 学历4e2ef732-2a50-4210-b1ae-5d880a1fa5cb,工龄a03ac012-c627-4795-ae3b-d50a28b7c01b
						REWARD_TYPE_NAME, -- 奖励名称 学历工龄
						REWARD_PROTECT_ID, -- 奖惩项目ID博士研究生9ee293ec-3810-40e5-9665-a54bdbd6a0c5/硕士研究生a2950e01-ee98-42fa-81c0-c723030fb358/
	-- 本科学历c6b08830-a55a-447d-8dcb-62606bf2eac8/大专学历9b95093f-c8a4-4461-8be2-80353dad9a1e/高中、中专12f9a56f-f440-404b-b8f8-3c690589f583,工龄/月
						REWARD_PROTECT_NAME, --   
						REWARD_DATE, -- 获得时间 10/1
						FRACTION, -- 惩奖分数 根据学历工龄
						DESCRIPTION, -- 说明
						`STATUS`, -- 审核状态0待审核1审核 1
						INPUT_ID, -- 添加人id张丽萍
						INPUT_NAME, --  张丽萍
						INPUT_DATE, -- 10/1
						REVIEW_ID, -- 审核人id 张丽萍
						REVIEW_NAME, -- 张丽萍
						REVIEW_DATE -- 10/1,
					)values (
								randIIDD,
								IIDD1,
								REALNAME1,
								CENTERID,
								CENTERVAL1,
								DEPARTMENT1,
								DEPARTMENTVAL1,
								'0',
								REWARD_TYPE_ID_v,
								REWARD_TYPE_NAME_v,
								protectid,
								protectname,
								ADDDATETIME1, -- '获得时间'
								fraction1,
								'系统导入,学历基础分数',
								'1',
								'72969d49-8645-474c-9399-1ba470f5886c', -- 张丽萍
								'张丽萍',
								'2019-10-01',
								'72969d49-8645-474c-9399-1ba470f5886c',
								'张丽萍',
								'2019-10-01'
						);
				end if;
			
			-- 学历每年增长的积分
			
			if EDUCATION1 is not null or EDUCATION1 != '' THEN
				-- 循环学历,每年加固定数
				set tempdate = DATE_ADD(ADDDATETIME1, INTERVAL 1 YEAR); -- 入职当年不算在其中
				xl_y:loop 
						-- 配置变量 
						set randIIDD = (select substring(md5(rand()), 1, 32));
						if EDUCATION1 = '高中、中专、初中' THEN  
							set protectid='da0059ba-ccff-46c1-8d35-3ef6d8479c3c'; set fraction1 = '0.5';
							ELSEIF  EDUCATION1 = '大专学历' THEN 
							set protectid='ca813aa6-b22c-49f0-9403-335fea324df5'; set fraction1 = '1';
							ELSEIF  EDUCATION1 = '本科学历' THEN 
							set protectid='cfb6ce59-30b7-4e93-921c-dff19f1a6693'; set fraction1 = '2';
							ELSEIF  EDUCATION1 = '硕士研究生' THEN 
							set protectid='d7615012-66b9-456c-b412-bafbd46a52f8'; set fraction1 = '3';
							ELSEIF  EDUCATION1 = '博士研究生' THEN 
							set protectid='ebe1fe7b-47b9-4af1-8e7a-7e060f3bcde2'; set fraction1 = '4';
							end if;
							
						-- 结束条件
						set tempdate = DATE_ADD(tempdate, INTERVAL 1 YEAR);
						if	tempdate > '2019-10-01' then  leave xl_y; end if;
										insert into record (
											RECORD_ID, -- 惩罚奖励记录id varchar类型
											USER_ID, -- 获奖id IIDD1
											USER_NAME, -- 获奖姓名 REALNAME1
											CORE_ID, -- 所属中心 CENTERID
											CORE_NAME, -- CENTERVAL1
											DEPARTMENT_ID, -- 所属部门 DEPARTMENT1
											DEPARTMENT_NAME, -- DEPARTMENTVAL1
											HAPPEN, -- 0加分1扣分 0
											REWARD_TYPE_ID, -- 奖惩类型ID 学历4e2ef732-2a50-4210-b1ae-5d880a1fa5cb,工龄a03ac012-c627-4795-ae3b-d50a28b7c01b
											REWARD_TYPE_NAME, -- 奖励名称 学历工龄
											REWARD_PROTECT_ID, -- 奖惩项目ID博士研究生9ee293ec-3810-40e5-9665-a54bdbd6a0c5/硕士研究生a2950e01-ee98-42fa-81c0-c723030fb358/
						-- 本科学历c6b08830-a55a-447d-8dcb-62606bf2eac8/大专学历9b95093f-c8a4-4461-8be2-80353dad9a1e/高中、中专12f9a56f-f440-404b-b8f8-3c690589f583,工龄/月
											REWARD_PROTECT_NAME, -- 项目名称  
											REWARD_DATE, -- 获得时间 10/1
											FRACTION, -- 惩奖分数 根据学历工龄
											DESCRIPTION, -- 说明
											`STATUS`, -- 审核状态0待审核1审核 1
											INPUT_ID, -- 添加人id张丽萍
											INPUT_NAME, --  张丽萍
											INPUT_DATE, -- 10/1
											REVIEW_ID, -- 审核人id 张丽萍
											REVIEW_NAME, -- 张丽萍
											REVIEW_DATE -- 10/1,
										)values (
													randIIDD,
													IIDD1,
													REALNAME1,
													CENTERID,
													CENTERVAL1,
													DEPARTMENT1,
													DEPARTMENTVAL1,
													'0',
													REWARD_TYPE_ID_v,
													REWARD_TYPE_NAME_v,
													protectid,
													concat(protectname,'/年'),-- 项目名称 学历/年
													tempdate, -- '获得时间'
													fraction1,
													'系统导入,每年按学历增长的分数',
													'1',
													'72969d49-8645-474c-9399-1ba470f5886c', -- 张丽萍
													'张丽萍',
													'2019-10-01',
													'72969d49-8645-474c-9399-1ba470f5886c',
													'张丽萍',
													'2019-10-01'
											);
				end loop;
			end if;


				-- 工龄增长分数5分,学历不为空的
				if EDUCATION1 is not null or EDUCATION1 != '' THEN
					-- 循环学历,每年加固定数
					set tempdate = ADDDATETIME1;
					gl_M:loop 
							-- 配置变量 
							set randIIDD = (select substring(md5(rand()), 1, 32));
							set protectid = 'a0233057-ae17-4d8d-94a8-9ae70e74b3dd';
							set REWARD_TYPE_ID_v = 'a03ac012-c627-4795-ae3b-d50a28b7c01b';
							set	REWARD_TYPE_NAME_v = '工龄';
							set protectname = '工龄/月';
							set fraction1 = '5';
							-- 结束条件
							set tempdate = DATE_ADD(tempdate, INTERVAL 1 MONTH);
							if	tempdate > '2019-10-01' then  leave gl_M; end if;
											insert into record (
												RECORD_ID, -- 惩罚奖励记录id varchar类型
												USER_ID, -- 获奖id IIDD1
												USER_NAME, -- 获奖姓名 REALNAME1
												CORE_ID, -- 所属中心 CENTERID
												CORE_NAME, -- CENTERVAL1
												DEPARTMENT_ID, -- 所属部门 DEPARTMENT1
												DEPARTMENT_NAME, -- DEPARTMENTVAL1
												HAPPEN, -- 0加分1扣分 0
												REWARD_TYPE_ID, -- 奖惩类型ID 学历4e2ef732-2a50-4210-b1ae-5d880a1fa5cb,工龄a03ac012-c627-4795-ae3b-d50a28b7c01b
												REWARD_TYPE_NAME, -- 奖励名称 学历工龄
												REWARD_PROTECT_ID, -- 奖惩项目ID博士研究生9ee293ec-3810-40e5-9665-a54bdbd6a0c5/硕士研究生a2950e01-ee98-42fa-81c0-c723030fb358/
							-- 本科学历c6b08830-a55a-447d-8dcb-62606bf2eac8/大专学历9b95093f-c8a4-4461-8be2-80353dad9a1e/高中、中专12f9a56f-f440-404b-b8f8-3c690589f583,工龄/月
												REWARD_PROTECT_NAME, -- 项目名称  
												REWARD_DATE, -- 获得时间 10/1
												FRACTION, -- 惩奖分数 根据学历工龄
												DESCRIPTION, -- 说明
												`STATUS`, -- 审核状态0待审核1审核 1
												INPUT_ID, -- 添加人id张丽萍
												INPUT_NAME, --  张丽萍
												INPUT_DATE, -- 10/1
												REVIEW_ID, -- 审核人id 张丽萍
												REVIEW_NAME, -- 张丽萍
												REVIEW_DATE -- 10/1,
											)values (
														randIIDD,
														IIDD1,
														REALNAME1,
														CENTERID,
														CENTERVAL1,
														DEPARTMENT1,
														DEPARTMENTVAL1,
														'0',
														REWARD_TYPE_ID_v,
														REWARD_TYPE_NAME_v,
														protectid,
														protectname,
														tempdate, -- '获得时间'
														fraction1,
														'系统导入,每月增长的工龄分数',
														'1',
														'72969d49-8645-474c-9399-1ba470f5886c', -- 张丽萍
														'张丽萍',
														'2019-10-01',
														'72969d49-8645-474c-9399-1ba470f5886c',
														'张丽萍',
														'2019-10-01'
												);
					end loop;
				end if;

				-- 汇总到人员表上
				if EDUCATION1 is not null or EDUCATION1 != '' THEN
				
					update member set INTERGRAL = (
						select SUM(FRACTION) FROM `record`
						where USER_ID = IIDD1
						GROUP BY USER_ID
					) where IIDD = IIDD1;
				end if;
			
		end loop;
    -- 关闭游标
    close mc;
		
		select '插入结束';
    
end