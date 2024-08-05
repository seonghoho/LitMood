<script setup>
import { RouterLink } from 'vue-router'
</script>

<template>
  <div class="navbar-div pt-[65px]">
    <header class="fixed top-0 left-0 right-0 h-[65px] bg-gray-800 text-white">
      <nav class="flex items-center justify-end h-full px-4">
        <RouterLink to="/" class="text-lg px-2 hover:text-gray-400">Home</RouterLink>
        <RouterLink to="/Book" class="text-lg px-2 hover:text-gray-400">Book</RouterLink>
        <RouterLink to="/Movie" class="text-lg px-2 hover:text-gray-400">Movie</RouterLink>
        <RouterLink to="/Music" class="text-lg px-2 hover:text-gray-400">Music</RouterLink>
      </nav>
    </header>
  </div>
</template>
