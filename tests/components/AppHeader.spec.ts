// Implements System
import { mount } from '@vue/test-utils';
import AppHeader from '@/components/AppHeader.vue';

describe('AppHeader', () => {
  it('renders provided title', () => {
    const wrapper = mount(AppHeader, {
      props: {
        title: '权限引导'
      }
    });
    expect(wrapper.text()).toContain('权限引导');
  });
});