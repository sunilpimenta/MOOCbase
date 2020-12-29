-- Before running drop any existing views
DROP VIEW IF EXISTS q0;
DROP VIEW IF EXISTS q1i;
DROP VIEW IF EXISTS q1ii;
DROP VIEW IF EXISTS q1iii;
DROP VIEW IF EXISTS q1iv;
DROP VIEW IF EXISTS q2i;
DROP VIEW IF EXISTS q2ii;
DROP VIEW IF EXISTS q2iii;
DROP VIEW IF EXISTS q3i;
DROP VIEW IF EXISTS q3ii;
DROP VIEW IF EXISTS q3iii;
DROP VIEW IF EXISTS q4i;
DROP VIEW IF EXISTS q4ii;
DROP VIEW IF EXISTS q4iii;
DROP VIEW IF EXISTS q4iv;
DROP VIEW IF EXISTS q4v;

-- Question 0
CREATE VIEW q0(era)
AS
  SELECT MAX(era)
  FROM pitching
;

-- Question 1i
CREATE VIEW q1i(namefirst, namelast, birthyear)
AS
  SELECT namefirst, namelast, birthyear
  FROM people
  WHERE weight>300
;

-- Question 1ii
CREATE VIEW q1ii(namefirst, namelast, birthyear)
AS
  SELECT namefirst, namelast, birthyear
  FROM people
  WHERE namefirst LIKE '_% _%'
  ORDER BY namefirst, namelast
;

-- Question 1iii
CREATE VIEW q1iii(birthyear, avgheight, count)
AS
  SELECT birthyear, AVG(height), COUNT(*)
  FROM people
  GROUP BY birthyear
  ORDER BY birthyear
;

-- Question 1iv
CREATE VIEW q1iv(birthyear, avgheight, count)
AS
  SELECT birthyear, avgheight, count
  FROM q1iii
  WHERE avgheight>70
  ORDER BY birthyear
;

-- Question 2i
CREATE VIEW q2i(namefirst, namelast, playerid, yearid)
AS
  SELECT p.namefirst, p.namelast, p.playerid, hof.yearid
  FROM people AS p, halloffame AS hof
  WHERE p.playerid=hof.playerid and hof.inducted='Y'
  ORDER BY hof.yearid DESC, p.playerid
;

-- Question 2ii
CREATE VIEW q2ii(namefirst, namelast, playerid, schoolid, yearid)
AS
  SELECT q2i.namefirst, q2i.namelast, cp.playerid, cp.schoolid, q2i.yearid
  FROM q2i, collegeplaying AS cp, schools AS s
  WHERE cp.playerid=q2i.playerid and cp.schoolid=s.schoolid and s.state='CA'
  ORDER BY q2i.yearid DESC, s.schoolid, q2i.playerid
;

-- Question 2iii
CREATE VIEW q2iii(playerid, namefirst, namelast, schoolid)
AS
  SELECT q2i.playerid, q2i.namefirst, q2i.namelast, cp.schoolid
  FROM q2i LEFT OUTER JOIN collegeplaying AS cp
  ON q2i.playerid=cp.playerid
  ORDER BY q2i.playerid DESC, cp.schoolid
;

-- Question 3i
CREATE VIEW q3i(playerid, namefirst, namelast, yearid, slg)
AS
  SELECT B.playerid as playerid, namefirst, namelast, yearid, (1.0 * (h - h2b - h3b - hr + 2*h2b + 3*h3b + 4*hr))/ab AS slg
  FROM people AS P, batting AS B
  WHERE P.playerid=B.playerid and ab>50
  ORDER BY slg DESC, yearid, playerid
  LIMIT 10
;

-- Question 3ii
CREATE VIEW q3ii(playerid, namefirst, namelast, lslg)
AS
  SELECT playerid, namefirst, namelast, (1.0 * h1b_sum + 2*h2b_sum + 3*h3b_sum + 4*hr_sum)/ab_sum AS lslg FROM (
    SELECT B.playerid as playerid, namefirst, namelast, SUM(h - h2b - h3b - hr) AS h1b_sum, SUM(h2b) AS h2b_sum, SUM(h3b) AS h3b_sum, SUM(hr) AS hr_sum, SUM(ab) AS ab_sum
    FROM people AS P, batting AS B
    WHERE P.playerid = B.playerid
    GROUP BY B.playerid, namefirst, namelast
  ) AS t
  WHERE ab_sum > 50
  ORDER BY lslg DESC, playerid
  LIMIT 10
;

-- Question 3iii
CREATE VIEW q3iii(namefirst, namelast, lslg)
AS
  WITH t2(playerid, namefirst, namelast, lslg) AS (
    SELECT playerid, namefirst, namelast, (1.0 * h1b_sum + 2*h2b_sum + 3*h3b_sum + 4*hr_sum)/ab_sum AS lslg FROM (
      SELECT B.playerid as playerid, namefirst, namelast, SUM(h - h2b - h3b - hr) AS h1b_sum, SUM(h2b) AS h2b_sum, SUM(h3b) AS h3b_sum, SUM(hr) AS hr_sum, SUM(ab) AS ab_sum
      FROM people AS P, batting AS B
      WHERE P.playerid = B.playerid
      GROUP BY B.playerid, namefirst, namelast
      ) AS t
    WHERE ab_sum > 50
  )
  SELECT namefirst, namelast, lslg 
  FROM t2
  WHERE lslg > ( SELECT lslg FROM t2 WHERE playerid = 'mayswi01')
  ORDER BY lslg DESC
;

-- Question 4i
CREATE VIEW q4i(yearid, min, max, avg)
AS
  SELECT yearid, MIN(salary), MAX(salary), AVG(salary)
  FROM salaries
  GROUP BY yearid
  ORDER BY yearid
;

-- Question 4ii
CREATE VIEW q4ii(binid, low, high, count)
AS 
  WITH binids AS (SELECT 0 i 
    UNION SELECT i+1 
    FROM binids 
    WHERE i<9
    ), X AS (
    SELECT MIN(salary) AS min, MAX(salary) AS max
    FROM salaries WHERE yearid = '2016'
    ), Y AS (
    SELECT i AS binid, i*(X.max-X.min)/10.0 + X.min AS low, (i+1) * (X.max-X.min)/10.0 + X.min AS high
    FROM binids, X)
  SELECT binid, low, high, COUNT(*)
  FROM Y INNER JOIN salaries AS s
  WHERE s.salary >= Y.low AND (s.salary < Y.high OR binid = 9 AND s.salary <= Y.high) AND yearid = '2016'
  GROUP BY binid, low, high
  ORDER BY binid ASC
;

-- Question 4iii
CREATE VIEW q4iii(yearid, mindiff, maxdiff, avgdiff)
AS
  SELECT cur.yearid, cur.min - pre.min, cur.max - pre.max, cur.avg - pre.avg
  FROM q4i AS cur
  INNER JOIN q4i AS pre
  ON cur.yearid - 1 = pre.yearid
  ORDER BY cur.yearid
;

-- Question 4iv
CREATE VIEW q4iv(playerid, namefirst, namelast, salary, yearid)
AS
  SELECT p.playerid, namefirst, namelast, salary, yearid
  FROM people AS p 
  INNER JOIN salaries AS s
  ON p.playerid = s.playerid
  WHERE (yearid BETWEEN 2000 AND 2001) AND (yearid, salary) IN (
    SELECT yearid, MAX(salary)
    FROM salaries
    GROUP BY yearid
  )
;

-- Question 4v
CREATE VIEW q4v(team, diffAvg) 
AS
  SELECT a.teamid as team, MAX(salary) - MIN(salary)
  FROM allstarfull AS a 
  INNER JOIN salaries AS s
  ON a.playerid = s.playerid and a.yearid = s.yearid
  WHERE a.yearid = 2016
  GROUP BY team
  ORDER BY team
;

