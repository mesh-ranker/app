package ru.meshrating.app

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM-тесты ядра Android-версии (запускаются без эмулятора: ./gradlew test).
 * Проверяют, что разбор данных МЭШ совпадает с desktop/web-версией.
 */
class MeshCoreTest {

    private val jwt = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9." +
        "eyJzdWIiOiIxMjM0NTY3ODkwIiwiZXhwIjo0MTAyNDQ0ODAwfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c"

    @Test
    fun `токен находится в JSON, тексте и URL`() {
        assertEquals(jwt, MeshCore.extractTokenFromText("""{"token":"$jwt"}"""))
        assertEquals(jwt, MeshCore.extractTokenFromText("""<b>"access_token": "$jwt"</b>"""))
        assertEquals(jwt, MeshCore.extractTokenFromText(jwt))
        assertEquals(jwt, MeshCore.extractTokenFromUrl("https://school.mos.ru/v2/token/refresh?token=$jwt&x=1"))
        assertEquals(jwt, MeshCore.extractTokenFromUrl("https://school.mos.ru/#access_token=$jwt"))
        assertNull(MeshCore.extractTokenFromText("здесь токена нет"))
    }

    @Test
    fun `looksLikeToken отсекает мусор`() {
        assertFalse(MeshCore.looksLikeToken("abc"))
        assertFalse(MeshCore.looksLikeToken("a".repeat(40)))
        assertFalse(MeshCore.looksLikeToken("mock-token-1"))
        assertTrue(MeshCore.looksLikeToken("Ab1Cd2Ef3Gh4Ij5Kl6Mn7Op8Qr9St0"))
    }

    @Test
    fun `describeToken показывает маску и срок действия`() {
        val info = MeshCore.describeToken(jwt)
        assertTrue(info.getBoolean("present"))
        assertEquals("jwt", info.getString("kind"))
        assertEquals(4_102_444_800_000L, info.getLong("expiresAt"))
        assertTrue(info.getString("mask").contains("…"))
        assertFalse(MeshCore.describeToken(null).getBoolean("present"))
    }

    @Test
    fun `normalizeProfile собирает родителя и детей`() {
        val profile = MeshCore.normalizeProfile(
            JSONObject(
                """{
                  "profile": {"id": 1, "last_name": "Демидова", "first_name": "Ольга", "middle_name": "Сергеевна"},
                  "children": [
                    {"id": 42, "contingent_guid": "${DemoMeshServer.UUID_ME}", "last_name": "Демидов", "first_name": "Пётр", "class_name": "7 «Б»"},
                    {"id": 43, "contingent_guid": "${DemoMeshServer.UUID_SIBLING}", "last_name": "Демидова", "first_name": "Аня"}
                  ]
                }"""
            )
        )
        assertEquals("Демидова Ольга Сергеевна", profile.getJSONObject("parent").getString("name"))
        val children = profile.getJSONArray("children")
        assertEquals(2, children.length())
        assertEquals(DemoMeshServer.UUID_ME, children.getJSONObject(0).getString("personId"))
        assertEquals("42", children.getJSONObject(0).getString("id"))
        assertEquals("Демидов Пётр", children.getJSONObject(0).getString("name"))
        assertEquals("42", profile.getJSONObject("defaultChild").getString("id"))
    }

    @Test
    fun `normalizeProfile берёт родителя из representatives и контакты ученика`() {
        val profile = MeshCore.normalizeProfile(
            JSONObject(
                """{
                  "profile": {"id": 1, "last_name": "Демидова", "first_name": "Ольга", "middle_name": "Сергеевна"},
                  "children": [
                    {"id": 42, "contingent_guid": "${DemoMeshServer.UUID_ME}", "last_name": "Демидов", "first_name": "Пётр", "class_name": "7 «Б»",
                     "email": "petr@example.ru", "phone": "9771429362",
                     "representatives": [
                       {"person_id": "rep-1", "last_name": "Демидова", "first_name": "Ольга", "middle_name": "Сергеевна", "type": "Родитель", "email": "olga@example.ru", "phone": "9299013256"}
                     ]}
                  ]
                }"""
            )
        )
        val parent = profile.getJSONObject("parent")
        assertEquals("Демидова Ольга Сергеевна", parent.getString("name"))
        assertEquals("rep-1", parent.getString("id"))
        assertEquals("Родитель", parent.getString("type"))
        assertEquals("olga@example.ru", parent.getString("email"))
        assertEquals("9299013256", parent.getString("phone"))
        val child = profile.getJSONArray("children").getJSONObject(0)
        assertEquals("petr@example.ru", child.getString("email"))
        assertEquals("9771429362", child.getString("phone"))
    }

    @Test
    fun `formatPhone приводит телефон к виду +7`() {
        assertEquals("+7 (977) 142-93-62", MeshCore.formatPhone("9771429362"))
        assertEquals("+7 (977) 142-93-62", MeshCore.formatPhone("79771429362"))
        assertEquals("+7 (977) 142-93-62", MeshCore.formatPhone("89771429362"))
        assertEquals("", MeshCore.formatPhone(null))
    }

    @Test
    fun `buildRankTable считает места, дельты и флаг это я`() {
        val members = JSONArray(
            """[
              {"personId": "b", "rank": {"averageMarkFive": 4.2, "rankPlace": 2, "rankStatus": "TOP"},
               "previousRank": {"averageMarkFive": 4.0, "rankPlace": 4}},
              {"personId": "a", "rank": {"averageMarkFive": 4.8, "rankPlace": 1, "rankStatus": "TOP"},
               "previousRank": {"averageMarkFive": 4.9, "rankPlace": 1}},
              {"personId": "c", "rank": {"averageMarkFive": 3.9, "rankPlace": 3}, "previousRank": {}}
            ]"""
        )
        val rows = MeshCore.buildRankTable(members, mapOf("a" to "Иванов Иван", "b" to "Петров Пётр"), "b")
        assertEquals(3, rows.length())
        assertEquals(1, rows.getJSONObject(0).getInt("place"))
        assertEquals("Иванов Иван", rows.getJSONObject(0).getString("name"))
        val me = rows.getJSONObject(1)
        assertEquals("Петров Пётр", me.getString("name"))
        assertTrue(me.getBoolean("isMe"))
        assertEquals(2, me.getInt("placeDelta")) // был 4-м, стал 2-м
        assertEquals(0.2, me.getDouble("averageDelta"), 0.001)
        assertEquals("green", rows.getJSONObject(0).getString("color"))
    }

    @Test
    fun `buildRankTable в месячном режиме использует показатели 30`() {
        val members = JSONArray(
            """[
              {"personId": "a",
               "rank": {"averageMarkFive": 4.0, "rankPlace": 5, "averageMarkFive30": 4.8, "rankPlace30": 1, "rankStatus30": "up"},
               "previousRank": {"averageMarkFive": 3.9, "rankPlace": 4, "averageMarkFive30": 4.5, "rankPlace30": 2}},
              {"personId": "b",
               "rank": {"averageMarkFive": 4.5, "rankPlace": 1, "averageMarkFive30": 3.9, "rankPlace30": 2, "rankStatus30": "down"},
               "previousRank": {"averageMarkFive": 4.4, "rankPlace": 1, "averageMarkFive30": 4.0, "rankPlace30": 1}}
            ]"""
        )

        val rows = MeshCore.buildRankTable(members, emptyMap(), "a", monthly = true)
        val first = rows.getJSONObject(0)
        assertEquals("a", first.getString("personId"))
        assertEquals(1, first.getInt("place"))
        assertEquals(4.8, first.getDouble("average"), 0.001)
        assertEquals("up", first.getString("status"))
        assertEquals(1, first.getInt("placeDelta")) // был 2-м, стал 1-м
        assertEquals(0.3, first.getDouble("averageDelta"), 0.001)
    }

    @Test
    fun `summarizeRank считает сводку по классу`() {
        val members = JSONArray(
            """[
              {"personId": "a", "rank": {"averageMarkFive": 5, "rankPlace": 1}},
              {"personId": "b", "rank": {"averageMarkFive": 4, "rankPlace": 2}},
              {"personId": "c", "rank": {"averageMarkFive": 3, "rankPlace": 3}}
            ]"""
        )
        val rows = MeshCore.buildRankTable(members, emptyMap(), "b")
        val summary = MeshCore.summarizeRank(rows, "b")
        assertEquals(3, summary.getInt("total"))
        assertEquals(4.0, summary.getDouble("classAverage"), 0.001)
        assertEquals(2, summary.getInt("myPlace"))
        assertEquals(1.0, summary.getDouble("gapToLeader"), 0.001)
        assertEquals(1, summary.getInt("aheadOfMe"))
        assertEquals(1, summary.getInt("behindMe"))
        assertEquals(1, summary.getInt("bestPlace"))
        assertEquals(6, summary.getJSONArray("histogram").length()) // 5.0, 4.5, 4.0, 3.5, 3.0, 2.5
    }

    @Test
    fun `summarizeMarks считает взвешенную оценку и итоги по предметам`() {
        val marks = listOf(
            MeshCore.normalizeMark(JSONObject("""{"value": 5, "weight": 1, "subject_id": 1, "subject_name": "Алгебра", "created_at": "2026-10-01"}""")),
            MeshCore.normalizeMark(JSONObject("""{"value": 4, "weight": 3, "subject_id": 1, "subject_name": "Алгебра", "created_at": "2026-10-02"}""")),
            MeshCore.normalizeMark(JSONObject("""{"value": 3, "weight": 1, "subject_id": 2, "subject_name": "Физика", "created_at": "2026-10-03"}"""))
        )
        val summary = MeshCore.summarizeMarks(marks)
        assertEquals(3, summary.getInt("count"))
        assertEquals(3, summary.getInt("withValue"))
        assertEquals(0, summary.getInt("withoutValue"))
        assertEquals(2, summary.getInt("subjectCount"))
        assertEquals(4.0, summary.getDouble("average"), 0.001)
        assertEquals(4.0, summary.getDouble("weightedAverage"), 0.001)
        assertEquals(4.0, summary.getDouble("median"), 0.001)
        assertEquals(3, summary.getInt("positiveCount"))
        assertEquals(1.0, summary.getDouble("positiveShare"), 0.001)
        assertEquals(1, summary.getInt("fivesCount"))
        assertEquals(2.0, summary.getDouble("range"), 0.001)
        assertEquals(2, summary.getJSONArray("bySubject").length())
        assertEquals(1, summary.getJSONArray("byValue").getJSONObject(0).getInt("count")) // пятёрок одна
        assertEquals("2026-10-03", summary.getJSONObject("lastMark").getString("date"))
    }

    @Test
    fun `нормализация домашки и расписания`() {
        val homework = MeshCore.normalizeHomework(
            JSONObject(
                """{"homework_entry_student_id": "h1", "homework_entry_id": 42,
                    "subject_name": "История", "description": "Параграф 12",
                    "date_assigned_on": "2026-10-06", "is_done": true}"""
            )
        )
        assertEquals("h1", homework.getString("id"))
        assertEquals("42", homework.getString("homeworkEntryId"))
        assertTrue(homework.getBoolean("isDone"))

        val lessons = MeshCore.normalizeSchedule(
            JSONObject(
                """{"payload": [
                     {"date": "2026-10-06", "lessons": [
                       {"lesson_number": 1, "begin_time": "08:30:00", "subject_name": "Алгебра", "room_name": "204"},
                       {"lesson_number": 2, "start_time": "09:25", "subjectName": "Геометрия", "room": "211"}]},
                     {"lesson_number": 3, "begin_time": "10:30", "subject_name": "Физика"}]}"""
            ),
            "2026-10-12"
        )
        assertEquals(3, lessons.length())
        assertEquals("08:30", lessons.getJSONObject(0).getString("begin"))
        assertEquals("Геометрия", lessons.getJSONObject(1).getString("subject"))
        assertEquals("2026-10-12", lessons.getJSONObject(2).getString("date"))
    }

    @Test
    fun `материалы ДЗ сохраняют uuid и достают ссылки`() {
        val homework = MeshCore.normalizeHomework(
            JSONObject(
                """{
                  "homework_entry_student_id": 100,
                  "homework_entry_id": 200,
                  "subject_id": 7,
                  "subject_name": "Физика",
                  "date_prepared_for": "2026-11-12T00:00:00",
                  "materials": [
                    {"type": "test_spec_binding", "type_name": "Тест", "title": "Тест по физике",
                     "action_id": 1, "action_name": "Пройти", "uuid": "test-uuid", "urls": []},
                    {"type": "fizikon_module", "type_name": "Облако знаний", "title": "Облако",
                     "action_id": 1, "action_name": "Пройти", "uuid": "cloud-uuid", "urls": []},
                    {"type": "attachments", "type_name": "Прочее", "title": "task.pdf", "id": 99,
                     "urls": [{"url": "/ej/attachments/files/task.pdf"}]}
                  ]
                }"""
            )
        )
        assertEquals("2026-11-12", homework.getString("dueDate"))
        assertEquals("200", homework.getString("homeworkEntryId"))
        val materials = homework.getJSONArray("materials")
        assertEquals(3, materials.length())
        assertEquals("test-uuid", materials.getJSONObject(0).getString("materialId"))
        assertEquals("cloud-uuid", materials.getJSONObject(1).getString("materialId"))
        assertEquals(
            "https://school.mos.ru/ej/attachments/files/task.pdf",
            materials.getJSONObject(2).getString("url")
        )
        val attachments = homework.getJSONArray("attachments")
        assertEquals(1, attachments.length())
        assertEquals(
            "https://school.mos.ru/ej/attachments/files/task.pdf",
            attachments.getJSONObject(0).getString("url")
        )
    }

    @Test
    fun `расписание показывает номер кабинета и id для перехода к ДЗ`() {
        val lessons = MeshCore.normalizeSchedule(
            JSONObject(
                """{"activities": [
                  {"type": "LESSON", "begin_time": "09:00", "end_time": "09:40",
                   "room_number": "412", "room_name": "Кабинет физики",
                   "lesson": {"subject_id": 33623584, "subject_name": "Физика",
                              "homework": "§ 6 - 16", "homework_entry_id": 354987379}},
                  {"type": "LESSON", "begin_time": "10:00", "end_time": "10:40",
                   "room_name": "Кабинет истории",
                   "lesson": {"subject_id": 33623645, "subject_name": "История"}}
                ]}"""
            ),
            "2026-11-12"
        )
        assertEquals(2, lessons.length())
        val physics = lessons.getJSONObject(0)
        assertEquals("09:40", physics.getString("end"))
        assertEquals("каб. 412", physics.getString("room"))
        assertEquals("33623584", physics.getString("subjectId"))
        assertEquals("354987379", physics.getString("homeworkId"))
        assertEquals("Кабинет истории", lessons.getJSONObject(1).getString("room"))
    }

    @Test
    fun `цвета и места считаются по правилам интерфейса`() {
        assertEquals("green", MeshCore.gradeColor(4.6))
        assertEquals("yellow", MeshCore.gradeColor(4.4))
        assertEquals("orange", MeshCore.gradeColor(3.4))
        assertEquals("red", MeshCore.gradeColor(2.4))
        assertEquals("none", MeshCore.gradeColor(null))
        assertEquals(2, MeshCore.placeOf(listOf(5.0, 4.0, 4.0, 3.0), 4.0))
        assertEquals("green", MeshCore.placeColor(1, 26))
        assertEquals("orange", MeshCore.placeColor(15, 26))
    }

    @Test
    fun `даты форматируются по-русски`() {
        assertEquals("2026-10-12", MeshCore.toIso("2026-10-12 09:00:00"))
        assertEquals("2026-10-12", MeshCore.toIso("12.10.2026"))
        assertEquals("12.10.2026", MeshCore.dateShort("2026-10-12"))
        assertEquals("12 октября 2026", MeshCore.dateRu("2026-10-12"))
        assertEquals("2026-09-01", MeshCore.schoolYearStart("2026-10-12"))
        assertEquals("2026-10-19", MeshCore.addDays("2026-10-12", 7))
    }

    @Test
    fun `описание ошибок понятно пользователю`() {
        assertTrue(MeshCore.describeError(MeshCore.MeshError("x", "network")).contains("school.mos.ru"))
        assertTrue(MeshCore.describeError(MeshCore.MeshError("x", "unauthorized")).contains("заново"))
        assertTrue(MeshCore.describeError(MeshCore.MeshError("x", "http-503", 503)).contains("503"))
        assertNotNull(MeshCore.describeError(RuntimeException("что-то пошло не так")))
    }

    @Test
    fun `subject_marks нормализует периоды и оценки`() {
        val raw = JSONArray(
            """[
              {
                "subject_id": 123,
                "subject_name": "Алгебра",
                "average": "4.50",
                "year_mark": "5",
                "dynamic": "UP",
                "periods": [
                  {
                    "title": "Первая четверть",
                    "start": "01.09",
                    "end": "23.10",
                    "value": "4.82",
                    "marks": [
                      {"id": 1, "value": "5", "control_form_name": "Устный ответ", "date": "2026-09-01", "comment": ""},
                      {"id": 2, "value": "4", "control_form_name": "Тест", "date": "2026-09-05", "comment": "Молодец"}
                    ]
                  }
                ]
              }
            ]"""
        )

        val subjects = MeshCore.normalizeSubjectMarks(raw)
        assertEquals(1, subjects.length())
        val subject = subjects.getJSONObject(0)
        assertEquals("123", subject.getString("subjectId"))
        assertEquals("Алгебра", subject.getString("subjectName"))
        assertEquals(4.5, subject.getDouble("average"), 0.001)
        assertEquals(5.0, subject.getDouble("yearMark"), 0.001)
        assertEquals(1, subject.getJSONArray("periods").length())

        val marks = subject.getJSONArray("marks")
        assertEquals(2, marks.length())
        assertEquals("2026-09-05", marks.getJSONObject(0).getString("date"))
        assertEquals("5", marks.getJSONObject(1).getString("markText"))
        assertEquals("Первая четверть", marks.getJSONObject(0).getString("periodName"))

        val markFromJournal = MeshCore.normalizeMark(
            JSONObject("""{"id": 3, "value": 4, "subject_id": 123, "date": "2026-09-12", "control_form_name": "Тест"}""")
        )
        val matchedPeriod = MeshCore.resolveMarkPeriod(markFromJournal, subjects)
        assertEquals("Первая четверть", matchedPeriod.getString("name"))
        assertFalse(matchedPeriod.getBoolean("approximate"))

        val periodsWithoutBoundaries = MeshCore.normalizeSubjectMarks(
            JSONArray(
                """[{"subject_id": 123, "subject_name": "Алгебра", "periods": [{"title": "Первый триместр", "marks": [{"id": 1, "value": 4, "date": "2026-09-12", "control_form_name": "Тест"}]}]}]"""
            )
        )
        val matchedByMarkData = MeshCore.resolveMarkPeriod(markFromJournal, periodsWithoutBoundaries)
        assertEquals("Первый триместр", matchedByMarkData.getString("name"))
        assertFalse(matchedByMarkData.getBoolean("approximate"))

        val markWithoutOfficialPeriod = MeshCore.normalizeMark(
            JSONObject("""{"value": 4, "subject_id": 123, "date": "2026-11-12"}""")
        )
        val guessedPeriod = MeshCore.resolveMarkPeriod(markWithoutOfficialPeriod, subjects)
        assertEquals("2-я четверть (ориентировочно)", guessedPeriod.getString("name"))
        assertTrue(guessedPeriod.getBoolean("approximate"))
    }

    @Test
    fun `subject_marks понимает плоский список чисел`() {
        val raw = JSONArray(
            """[{"subject_id": 7, "subject_name": "Физика", "marks": [5, 4, 3], "average": 4.0, "final_mark": 4}]"""
        )

        val subject = MeshCore.normalizeSubjectMarks(raw).getJSONObject(0)
        assertEquals(3, subject.getJSONArray("marks").length())
        assertEquals(4.0, subject.getDouble("yearMark"), 0.001)
    }
}
