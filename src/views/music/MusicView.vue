<script setup>
import { useCounterStore } from '@/stores/counter.js'

const counterStore = useCounterStore()
</script>

<template>
  <div>
    음악
    <div>
      <p>Count: {{ counterStore.count }}</p>
      <p>Double Count: {{ counterStore.doubleCount }}</p>
      <button @click="counterStore.increment">Increment</button>
    </div>
  </div>
</template>
