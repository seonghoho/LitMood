<script setup>
import BookCard from '@/components/book/BookCard.vue'
import { useBookStore } from '@/stores'

const bookStore = useBookStore()

const books = bookStore.fetchSearchBook()

console.log(books.bookList)
</script>

<template>
  <div>
    <BookCard />
  </div>
</template>
