export type Material = { id: string; status: keyof typeof statuses } & Record<string, unknown>
export type Field = { name: string; label: string; options?: readonly string[]; optional?: boolean; example?: object[]; multiline?: boolean }
export const statuses = { DRAFT: 'Bản nháp', ACTIVE: 'Công khai', ARCHIVED: 'Đã ẩn' }
export const voices = ['en-us-voice-a-v1', 'en-us-voice-b-v1']
const lesson = { name: 'lesson_id', label: 'ID bài học' }
const order = { name: 'sort_order', label: 'Thứ tự hiển thị' }
const title = { name: 'title', label: 'Tiêu đề' }
const dialogueExample = [
  ['Khách', 'Can I change my booking?', 'Tôi có thể đổi đặt phòng không?'],
  ['Lễ tân', 'Of course. What date works for you?', 'Dĩ nhiên. Ngày nào phù hợp với bạn?'],
  ['Khách', 'Can I come on Friday?', 'Tôi đến vào thứ Sáu được không?'],
  ['Lễ tân', 'Yes, we have a room on Friday.', 'Được, thứ Sáu chúng tôi còn phòng.'],
].map(([speaker, english, vietnamese], index) => ({
  line_no: index + 1, speaker_label: speaker, text_en: english, text_vi: vietnamese, voice_key: voices[index % 2],
}))

export const definitions = {
  TOPICS: { label: 'Chủ đề', labelField: 'name', fields: [
    { name: 'code', label: 'Mã chủ đề' }, { name: 'name', label: 'Tên chủ đề' },
    { name: 'description', label: 'Mô tả', multiline: true, optional: true }, order,
  ] },
  LESSONS: { label: 'Bài học', labelField: 'title', fields: [
    { name: 'topic_id', label: 'ID chủ đề' }, { name: 'level', label: 'Cấp độ', options: ['A2', 'B1'] },
    { name: 'scenario_id', label: 'ID tình huống gợi ý', optional: true }, title,
    { name: 'summary', label: 'Tóm tắt', multiline: true }, { name: 'objective_code', label: 'Mã mục tiêu' },
    { name: 'learning_objective', label: 'Mục tiêu học', multiline: true },
  ] },
  WORDS: { label: 'Từ và mẫu câu', labelField: 'expression', fields: [
    lesson, { name: 'kind', label: 'Loại', options: ['WORD', 'PHRASE'] },
    { name: 'expression', label: 'Từ hoặc mẫu câu tiếng Anh' }, { name: 'ipa', label: 'IPA · ví dụ /həˈloʊ/', optional: true },
    { name: 'meaning_vi', label: 'Nghĩa tiếng Việt' }, { name: 'example_en', label: 'Ví dụ tiếng Anh', optional: true },
    { name: 'example_vi', label: 'Bản dịch ví dụ', optional: true }, order,
  ] },
  DIALOGUES: { label: 'Hội thoại mẫu', labelField: 'title', fields: [
    lesson, title, { name: 'lines_json', label: 'Lời thoại tiếng Anh và bản dịch · JSON', example: dialogueExample }, order,
  ] },
  LISTENING: { label: 'Đề nghe', labelField: 'title', fields: [
    lesson, title, { name: 'instructions', label: 'Hướng dẫn làm bài', multiline: true },
    { name: 'tts_voice_key', label: 'Giọng mặc định', options: voices },
    { name: 'transcript_en', label: 'Kịch bản tiếng Anh', multiline: true },
    { name: 'translation_vi', label: 'Bản dịch', multiline: true, optional: true },
    { name: 'script_segments_json', label: 'Các lượt nói · JSON', optional: true, example: dialogueExample.map(line => ({ line_no: line.line_no, speaker_label: line.speaker_label,
      text_en: line.text_en, voice_key: line.voice_key })) },
    { name: 'questions_json', label: 'Câu hỏi và đáp án · JSON', example: [{
      code: 'q1', type: 'DICTATION', prompt: 'Gõ lại câu đã nghe.', options: [],
      accepted: ['Can I change my booking?'], explanationVi: 'Người nói muốn đổi đặt phòng.',
    }] }, order,
  ] },
  PRONUNCIATION: { label: 'Bài phát âm', labelField: 'reference_text', fields: [
    lesson, { name: 'reference_text', label: 'Văn bản đọc mẫu', multiline: true },
    { name: 'locale', label: 'Giọng chấm', options: ['en-US'] },
    { name: 'mode', label: 'Cách luyện', options: ['READ_ALOUD', 'SHADOWING'] }, order,
  ] },
} satisfies Record<string, { label: string; labelField: string; fields: Field[] }>

export type Kind = keyof typeof definitions
export type Editor = { source?: Material; copy?: boolean }

