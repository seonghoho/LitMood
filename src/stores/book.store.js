import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useBookStore = defineStore('book', () => {
  const bookList = ref(null)

  // 책 fetch 함수
  const fetchSearchBook = async () => {
    const url = `/v1/search/book.json?query=%EC%A3%BC%EC%8B%9D&display=10&start=1`
    const clientId = '_mYu6z5I9DKkT3Ujn6h7'
    const clientSecret = '0vTusnMioo'

    const headers = {
      Accept: 'application/json',
      'X-Naver-Client-Id': clientId,
      'X-Naver-Client-Secret': clientSecret,
    }

    try {
      const response = await fetch(url, { headers })
      const data = await response.json()
      bookList.value.push(data.items)
    } catch (err) {
      console.log(err)
    }
  }

  return { bookList, fetchSearchBook }
})
