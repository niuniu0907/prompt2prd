import { createRouter, createWebHistory, type RouterHistory } from 'vue-router'

import ProjectHomeView from '@/views/ProjectHomeView.vue'
import NewProjectView from '@/views/NewProjectView.vue'
import ProjectStartView from '@/views/ProjectStartView.vue'

export function createAppRouter(history: RouterHistory = createWebHistory()) {
  return createRouter({
    history,
    routes: [
      {
        path: '/',
        name: 'project-home',
        component: ProjectHomeView,
      },
      {
        path: '/projects/new',
        name: 'new-project',
        component: NewProjectView,
      },
      {
        path: '/projects/:projectId',
        name: 'project-start',
        component: ProjectStartView,
      },
    ],
  })
}

export const router = createAppRouter()
