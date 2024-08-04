import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '../views/HomeView.vue'
import BookView from '@/components/book/BookView.vue'
import MovieView from '@/components/movie/MovieView.vue'
import MusicView from '@/components/music/MusicView.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'home',
      component: HomeView
    },
    {
      path: '/book',
      name: 'book',
      component: BookView
    },
    {
      path: '/movie',
      name: 'movie',
      component: MovieView
    },
    {
      path: '/music',
      name: 'music',
      component: MusicView
    }
    // {
    //   path: '/about',
    //   name: 'about',
    //   // route level code-splitting
    //   // this generates a separate chunk (About.[hash].js) for this route
    //   // which is lazy-loaded when the route is visited.
    //   component: () => import('../views/AboutView.vue')
    // }
  ]
})

export default router
