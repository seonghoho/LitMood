<script setup>
import NavBar from '@/common/NavBar.vue'
import { RouterView } from 'vue-router'

//   &lt;
</script>

<template>
  <header>
    <NavBar />
  </header>
  <body>
    <!-- 해당 url에 route된 페이지 출력 -->
    <RouterView />
  </body>
</template>

<style scoped></style>
